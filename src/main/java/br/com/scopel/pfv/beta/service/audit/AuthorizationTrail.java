package br.com.scopel.pfv.beta.service.audit;

import br.com.scopel.pfv.beta.model.User;

public final class AuthorizationTrail {

    private static final ThreadLocal<User> REQUESTER = new ThreadLocal<>();

    private AuthorizationTrail() {
    }

    public static void begin(User requester) {
        REQUESTER.set(requester);
    }

    public static void end() {
        REQUESTER.remove();
    }

    public static User requester() {
        return REQUESTER.get();
    }

    public static String describe(User actor, String description) {
        User requester = REQUESTER.get();
        if (requester == null || actor == null || requester == actor) {
            return description;
        }
        return description + " - editado por " + nameOf(requester) + ", autorizado por " + nameOf(actor);
    }

    private static String nameOf(User user) {
        return user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getLogin();
    }
}
