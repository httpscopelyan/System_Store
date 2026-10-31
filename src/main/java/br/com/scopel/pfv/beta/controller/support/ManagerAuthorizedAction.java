package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.ManagerAuthorizationController;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.User;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.Supplier;

public final class ManagerAuthorizedAction {

    @FunctionalInterface
    public interface Action<T> {
        T run(User authorizedBy);
    }

    private final ManagerAuthorizationController authorizationDialog;
    private final UserSession userSession;
    private final Supplier<Window> ownerWindow;

    public ManagerAuthorizedAction(ManagerAuthorizationController authorizationDialog, UserSession userSession,
                                   Supplier<Window> ownerWindow) {
        this.authorizationDialog = authorizationDialog;
        this.userSession = userSession;
        this.ownerWindow = ownerWindow;
    }

    public <T> T apply(Action<T> action) {
        try {
            return action.run(userSession.getLoggedUser());
        } catch (ManagerAuthorizationRequiredException needsManager) {
            Optional<User> authorizingManager = authorizationDialog.requestAuthorization(ownerWindow.get());
            if (authorizingManager.isEmpty()) {
                return null;
            }
            return action.run(authorizingManager.get());
        }
    }
}
