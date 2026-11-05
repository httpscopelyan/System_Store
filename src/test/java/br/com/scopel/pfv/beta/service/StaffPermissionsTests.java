package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffPermissionsTests {

    private User user(String login, User.Role role) {
        User user = new User();
        user.setLogin(login);
        user.setName(login);
        user.setRole(role);
        return user;
    }

    @Test
    void dayanaAndJoaoEditStockQuantityEvenAsOperators() {
        assertTrue(StaffPermissions.canEditStockQuantity(user("Dayana", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canEditStockQuantity(user("Joao", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canEditStockQuantity(user("  JOÃO ", User.Role.OPERADOR)));
    }

    @Test
    void nobodyElseEditsStockQuantityNotEvenManagers() {
        assertFalse(StaffPermissions.canEditStockQuantity(user("Maria", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.canEditStockQuantity(user("Ana Clara", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.canEditStockQuantity(null));
        assertFalse(StaffPermissions.canEditStockQuantity(user("Chefe", User.Role.GERENTE)));
    }

    @Test
    void anaClaraCannotRegisterProductsButOthersCan() {
        assertFalse(StaffPermissions.canRegisterProducts(user("Ana Clara", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.canRegisterProducts(user("ana  clara", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canRegisterProducts(user("Dayana", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canRegisterProducts(user("Chefe", User.Role.GERENTE)));
    }

    @Test
    void privilegedNamesAreReservedSoNobodyCanRenameIntoThem() {
        assertTrue(StaffPermissions.isReservedName("Dayana"));
        assertTrue(StaffPermissions.isReservedName("João"));
        assertTrue(StaffPermissions.isReservedName("Ana Clara"));
        assertFalse(StaffPermissions.isReservedName("Maria"));
    }

    @Test
    void dayanaAndManagersEditSalePriceButOtherOperatorsDoNot() {
        assertTrue(StaffPermissions.canEditPrices(user("Dayana", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canEditPrices(user("Chefe", User.Role.GERENTE)));
        assertFalse(StaffPermissions.canEditPrices(user("Joao", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.canEditPrices(user("Ana Clara", User.Role.OPERADOR)));
    }

    @Test
    void onlyAnaClaraIsViewOnly() {
        assertTrue(StaffPermissions.isViewOnly(user("Ana Clara", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.isViewOnly(user("Dayana", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.isViewOnly(null));
    }
}
