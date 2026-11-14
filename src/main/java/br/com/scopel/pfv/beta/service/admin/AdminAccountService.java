package br.com.scopel.pfv.beta.service.admin;

import br.com.scopel.pfv.beta.exception.UserDuplicatedException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.PermissionDefaults;
import br.com.scopel.pfv.beta.service.StaffPermissions;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminAccountService {

    static final int MAX_NAME_LENGTH = 30;
    static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService audit;

    public AdminAccountService(UserRepository users, PasswordEncoder passwordEncoder, AuditLogService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AdminAccount> accounts(User actor) {
        AdminAccess.requireDev(actor);
        return users.findAllByOrderByNameAsc().stream().map(this::toAccount).toList();
    }

    @Transactional
    public AdminAccount create(User actor, String name, String password, User.Role role) {
        AdminAccess.requireDev(actor);
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("O nome deve ter de 1 a " + MAX_NAME_LENGTH + " caracteres.");
        }
        if (role == null || role == User.Role.DEV) {
            throw new IllegalArgumentException("Escolha o perfil Operador ou Gerente.");
        }
        requirePassword(password);
        if (users.findByLogin(normalizedName).isPresent() || StaffPermissions.isReservedName(normalizedName)) {
            throw new UserDuplicatedException();
        }
        User created = new User();
        created.setLogin(normalizedName);
        created.setName(normalizedName);
        created.setPassword(passwordEncoder.encode(password));
        created.setRole(role);
        created.setPermissions(PermissionDefaults.forAccount(normalizedName, normalizedName, role));
        User saved = users.save(created);
        audit.record(actor, AuditAction.USER_CREATED, AuditEntityType.USER, saved.getId(),
                "Conta " + normalizedName + " criada como " + role, null, role.name());
        return toAccount(saved);
    }

    @Transactional
    public void setActive(User actor, Long userId, boolean active) {
        AdminAccess.requireDev(actor);
        User target = editable(actor, userId);
        if (!active && target.getId().equals(actor.getId())) {
            throw new IllegalArgumentException("Você não pode inativar a própria conta.");
        }
        if (Boolean.valueOf(active).equals(target.getActive())) {
            return;
        }
        target.setActive(active);
        users.save(target);
        audit.record(actor, active ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                AuditEntityType.USER, target.getId(),
                "Conta " + target.getLogin() + (active ? " reativada" : " inativada"),
                String.valueOf(!active), String.valueOf(active));
    }

    @Transactional
    public void delete(User actor, Long userId) {
        AdminAccess.requireDev(actor);
        User target = editable(actor, userId);
        if (target.getId().equals(actor.getId())) {
            throw new IllegalArgumentException("Você não pode excluir a própria conta.");
        }
        try {
            users.delete(target);
            users.flush();
        } catch (DataIntegrityViolationException hasHistory) {
            throw new IllegalStateException(
                    "A conta " + target.getLogin() + " tem histórico no sistema e não pode ser excluída. Inative-a.");
        }
        audit.record(actor, AuditAction.USER_DELETED, AuditEntityType.USER, userId,
                "Conta " + target.getLogin() + " excluída", target.getRole().name(), null);
    }

    @Transactional
    public void changeRole(User actor, Long userId, User.Role role) {
        AdminAccess.requireDev(actor);
        if (role == null || role == User.Role.DEV) {
            throw new IllegalArgumentException("Escolha o perfil Operador ou Gerente.");
        }
        User target = editable(actor, userId);
        User.Role previous = target.getRole();
        if (previous == role) {
            return;
        }
        target.setRole(role);
        users.save(target);
        audit.record(actor, AuditAction.USER_ROLE_CHANGED, AuditEntityType.USER, target.getId(),
                "Perfil de " + target.getLogin() + " alterado", previous.name(), role.name());
    }

    @Transactional
    public void resetPassword(User actor, Long userId, String newPassword) {
        AdminAccess.requireDev(actor);
        requirePassword(newPassword);
        User target = users.findById(userId).orElseThrow(UserNotFindException::new);
        if (StaffPermissions.isAdministrator(target) && !target.getId().equals(actor.getId())) {
            throw new IllegalArgumentException("A senha da conta administradora só pode ser trocada por ela mesma.");
        }
        target.setPassword(passwordEncoder.encode(newPassword));
        users.save(target);
        audit.record(actor, AuditAction.USER_PASSWORD_RESET, AuditEntityType.USER, target.getId(),
                "Senha de " + target.getLogin() + " redefinida", null, null);
    }

    @Transactional
    public void setPermissions(User actor, Long userId, Set<Permission> permissions) {
        AdminAccess.requireDev(actor);
        User target = editable(actor, userId);
        Set<Permission> wanted = permissions == null || permissions.isEmpty()
                ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
        Set<Permission> previous = target.getPermissions().isEmpty()
                ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(target.getPermissions());
        if (previous.equals(wanted)) {
            return;
        }
        target.setPermissions(wanted);
        users.save(target);
        audit.record(actor, AuditAction.USER_PERMISSIONS_CHANGED, AuditEntityType.USER, target.getId(),
                "Permissões de " + target.getLogin() + " alteradas", previous.toString(), wanted.toString());
    }

    private User editable(User actor, Long userId) {
        User target = users.findById(userId).orElseThrow(UserNotFindException::new);
        if (StaffPermissions.isAdministrator(target) || target.getRole() == User.Role.DEV) {
            throw new IllegalArgumentException("A conta DEV não pode ser alterada por aqui.");
        }
        return target;
    }

    private void requirePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("A senha deve ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
    }

    private AdminAccount toAccount(User user) {
        boolean protectedAccount = StaffPermissions.isAdministrator(user) || user.getRole() == User.Role.DEV;
        return new AdminAccount(user.getId(), user.getName(), user.getLogin(), user.getRole(),
                Boolean.TRUE.equals(user.getActive()),
                user.getCashGroup() == null ? null : user.getCashGroup().getId(),
                user.getCashGroup() == null ? null : user.getCashGroup().getName(),
                user.getPermissions().isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(user.getPermissions()),
                protectedAccount);
    }
}
