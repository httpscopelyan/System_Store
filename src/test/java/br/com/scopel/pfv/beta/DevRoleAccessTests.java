package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.StaffPermissions;
import br.com.scopel.pfv.beta.service.audit.AuditActorContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevRoleAccessTests {

    private User userWith(String login, User.Role role) {
        User user = new User();
        user.setLogin(login);
        user.setName(login);
        user.setRole(role);
        return user;
    }

    @Test
    void devAndManagerHaveManagerAccessButOperatorDoesNot() {
        assertTrue(userWith("Yan", User.Role.DEV).hasManagerAccess());
        assertTrue(userWith("Chefe", User.Role.GERENTE).hasManagerAccess());
        assertFalse(userWith("Caixa", User.Role.OPERADOR).hasManagerAccess());
    }

    @Test
    void devCanEditPrices() {
        assertTrue(StaffPermissions.canEditPrices(userWith("Yan", User.Role.DEV)));
    }

    @Test
    void actorContextKeepsTheLastLoggedUser() {
        User logged = userWith("Dayana", User.Role.OPERADOR);
        AuditActorContext.set(logged);
        assertSame(logged, AuditActorContext.get());
        AuditActorContext.set(null);
        assertNull(AuditActorContext.get());
    }
}
