package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.UserDuplicatedException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final int MAX_LOGIN_LENGTH = 30;
    private static final int MIN_PASSWORD_LENGTH = 4;

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder; 

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public User findLogin(String login) {
        return repository.findByLogin(login)
                .orElseThrow(() -> new UserNotFindException());
    }

    public User save(User newUser) {
        if (repository.findByLogin(newUser.getLogin()).isPresent()) {
            throw new UserDuplicatedException();
        }
        String hash = passwordEncoder.encode(newUser.getPassword());
        newUser.setPassword(hash);

        return repository.save(newUser);
    }

    public boolean registerWithPasswordHash(String login, String name, String passwordHash, User.Role role) {
        if (repository.findByLogin(login).isPresent()) {
            return false;
        }
        User user = new User();
        user.setLogin(login);
        user.setName(name);
        user.setPassword(passwordHash);
        user.setRole(role);
        repository.save(user);
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
        if (renamed && StaffPermissions.isReservedName(login)) {
            throw new UserDuplicatedException();
        }
        boolean takenByAnother = repository.findByLogin(login)
                .filter(other -> !other.getId().equals(user.getId()))
                .isPresent();
        if (takenByAnother) {
            throw new UserDuplicatedException();
        }
        user.setLogin(login);
        user.setName(login);
        if (newPassword != null && !newPassword.isEmpty()) {
            if (newPassword.length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalArgumentException("A senha nova deve ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres.");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        return repository.save(user);
    }

    public boolean hasUsers() {
        return repository.count() > 0;
    }

    public User login(String login, String rawPassword) {
        User user = repository.findByLogin(login)
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
        User authenticatedUser = login(managerLogin, rawPassword);
        if (authenticatedUser.getRole() != User.Role.GERENTE) {
            throw new ManagerAuthorizationRequiredException();
        }
        return authenticatedUser;
    }

    public User modificationRole(String login, User.Role role) {
        User n = repository.findByLogin(login)
                .orElseThrow(() -> new UserNotFindException());
        n.setRole(role);
        return repository.save(n);
    }
}