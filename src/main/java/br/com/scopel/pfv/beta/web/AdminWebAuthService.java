package br.com.scopel.pfv.beta.web;

import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.UserService;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AdminWebAuthService {

    private final UserService userService;
    private final UserRepository users;
    private final AuditLogService audit;

    public AdminWebAuthService(UserService userService, UserRepository users, AuditLogService audit) {
        this.userService = userService;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public Optional<User> login(String login, String password, String client) {
        String shownLogin = login == null ? "" : login.trim();
        try {
            User user = userService.authenticateDev(shownLogin, password == null ? "" : password);
            audit.record(user, AuditAction.ADMIN_WEB_LOGIN, AuditEntityType.USER, user.getId(),
                    "Login no painel web de " + user.getLogin() + " (origem " + client + ")", null, null);
            return Optional.of(user);
        } catch (RuntimeException rejected) {
            audit.recordExternal("Web " + client, AuditAction.ADMIN_WEB_LOGIN_FAILED, AuditEntityType.USER, null,
                    "Tentativa de login recusada no painel web para '" + clip(shownLogin) + "' (origem " + client + ")");
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<User> activeDev(Long userId) {
        return users.findById(userId)
                .filter(user -> user.getRole() == User.Role.DEV && Boolean.TRUE.equals(user.getActive()));
    }

    private String clip(String text) {
        return text.length() > 40 ? text.substring(0, 40) : text;
    }
}
