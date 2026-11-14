package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.UserDuplicatedException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.AccountSummary;
import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private static final int MAX_LOGIN_LENGTH = 30;
    private static final int MIN_PASSWORD_LENGTH = 4;

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder,
                       AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public User findLogin(String login) {
        return repository.findByLogin(login)
                .orElseThrow(() -> new UserNotFindException());
    }

    @Transactional
    public User save(User newUser) {
        if (repository.findByLogin(newUser.getLogin()).isPresent()) {
            throw new UserDuplicatedException();
        }
        String hash = passwordEncoder.encode(newUser.getPassword());
        newUser.setPassword(hash);
        applyDefaultPermissions(newUser);

        User saved = repository.save(newUser);
        auditLogService.record(null, AuditAction.USER_CREATED, AuditEntityType.USER, saved.getId(),
                "Usuario " + saved.getLogin() + " criado como " + saved.getRole(), null, saved.getRole().name());
        return saved;
    }

    @Transactional
    public boolean registerWithPasswordHash(String login, String name, String passwordHash, User.Role role) {
        if (repository.findByLogin(login).isPresent()) {
            return false;
        }
        User user = new User();
        user.setLogin(login);
        user.setName(name);
        user.setPassword(passwordHash);
        user.setRole(role);
        applyDefaultPermissions(user);
        User saved = repository.save(user);
        auditLogService.record(null, AuditAction.USER_CREATED, AuditEntityType.USER, saved.getId(),
                "Usuario " + login + " criado como " + role, null, role.name());
        return true;
    }

    @Transactional
    public User updateOwnCredentials(Long userId, String currentPassword, String newLogin, String newPassword) {
        User user = repository.findById(userId).orElseThrow(() -> new UserNotFindException());
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidPasswordException();
        }
        String login = newLogin == null ? "" : newLogin.trim();
        if (login.isEmpty() || login.length() > MAX_LOGIN_LENGTH) {
            throw new IllegalArgumentException("O nome deve ter de 1 a " + MAX_LOGIN_LENGTH + " caracteres.");
        }
        boolean renamed = !login.equals(user.getLogin());
        if (renamed && StaffPermissions.isViewOnly(user)) {
            throw new IllegalArgumentException("Esta conta não pode trocar o nome. Peça ao gerente.");
        }
        if (renamed && StaffPermissions.isAdministrator(user)) {
            throw new IllegalArgumentException("A conta administradora não pode trocar o nome.");
        }
        if (renamed && StaffPermissions.isReservedName(login)) {
            throw new UserDuplicatedException();
        }
        boolean takenByAnother = repository.findByLogin(login)
                .filter(other -> !other.getId().equals(user.getId()))
                .isPresent();
        if (takenByAnother) {
            throw new UserDuplicatedException();
        }
        String previousLogin = user.getLogin();
        user.setLogin(login);
        user.setName(login);
        if (newPassword != null && !newPassword.isEmpty()) {
            if (newPassword.length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalArgumentException("A senha nova deve ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres.");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        User updated = repository.save(user);
        auditLogService.record(updated, AuditAction.USER_UPDATED, AuditEntityType.USER, updated.getId(),
                "Usuario " + previousLogin + " alterou os proprios dados"
                        + (newPassword != null && !newPassword.isEmpty() ? " (senha trocada)" : ""),
                previousLogin, login);
        return updated;
    }

    public boolean hasUsers() {
        return repository.count() > 0;
    }

    public List<AccountSummary> listActiveAccounts() {
        return repository.findActiveAccounts();
    }

    @Transactional
    public User login(String login, String rawPassword) {
        User user = authenticate(login, rawPassword);
        auditLogService.record(user, AuditAction.LOGIN, AuditEntityType.USER, user.getId(),
                "Login de " + user.getLogin(), null, null);
        return user;
    }

    public User authenticateDev(String login, String rawPassword) {
        User user = authenticate(login, rawPassword);
        if (user.getRole() != User.Role.DEV) {
            throw new ManagerAuthorizationRequiredException();
        }
        return user;
    }

    private void applyDefaultPermissions(User user) {
        if (user.getPermissions().isEmpty()) {
            user.setPermissions(PermissionDefaults.forAccount(user.getLogin(), user.getName(), user.getRole()));
        }
    }

    private User authenticate(String login, String rawPassword) {
        User user = repository.findByLogin(login)
                .filter(found -> Boolean.TRUE.equals(found.getActive()))
                .orElseThrow(() -> new UserNotFindException());

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidPasswordException();
        }
        return user;
    }

    /**
     * Autoriza uma ação sensível com as credenciais de um gerente (RN08/RF18/RF21).
     * Reusa a verificação BCrypt de {@link #login} (login inexistente -> UserNotFindException,
     * senha incorreta -> InvalidPasswordException) e, só então, exige o papel GERENTE.
     * O operador logado NÃO é trocado: este método apenas devolve o gerente que autorizou.
     * Nenhuma mensagem de exceção carrega a senha informada.
     */
    public User authenticateManager(String managerLogin, String rawPassword) {
        User authenticatedUser = authenticate(managerLogin, rawPassword);
        if (!authenticatedUser.hasManagerAccess()) {
            throw new ManagerAuthorizationRequiredException();
        }
        return authenticatedUser;
    }

    @Transactional
    public User modificationRole(String login, User.Role role) {
        User n = repository.findByLogin(login)
                .orElseThrow(() -> new UserNotFindException());
        if (StaffPermissions.isAdministrator(n)) {
            throw new IllegalArgumentException("A conta administradora não pode ter o perfil alterado.");
        }
        User.Role previousRole = n.getRole();
        n.setRole(role);
        User changed = repository.save(n);
        auditLogService.record(null, AuditAction.USER_ROLE_CHANGED, AuditEntityType.USER, changed.getId(),
                "Perfil de " + changed.getLogin() + " alterado", String.valueOf(previousRole), String.valueOf(role));
        return changed;
    }
}