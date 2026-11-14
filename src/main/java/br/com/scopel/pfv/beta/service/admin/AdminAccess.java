package br.com.scopel.pfv.beta.service.admin;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.User;

public final class AdminAccess {

    private AdminAccess() {
    }

    public static void requireDev(User actor) {
        if (actor == null || actor.getRole() != User.Role.DEV || !Boolean.TRUE.equals(actor.getActive())) {
            throw new ManagerAuthorizationRequiredException();
        }
    }
}
