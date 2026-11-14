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
        user.setPermissions(PermissionDefaults.forAccount(login, login, role));
        return user;
    }

    @Test
    void dayanaJoaoAndAnaClaraEditStockQuantityEvenAsOperators() {
        assertTrue(StaffPermissions.canEditStockQuantity(user("Dayana", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canEditStockQuantity(user("Joao", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canEditStockQuantity(user("  JOÃO ", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canEditStockQuantity(user("Ana Clara", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canEditStockQuantity(user("ana  clara", User.Role.OPERADOR)));
    }

    @Test
    void nobodyElseEditsStockQuantityNotEvenManagers() {
        assertFalse(StaffPermissions.canEditStockQuantity(user("Maria", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.canEditStockQuantity(null));
        assertFalse(StaffPermissions.canEditStockQuantity(user("Chefe", User.Role.GERENTE)));
    }

    @Test
    void anaClaraRegistersProductsWithoutSeeingCostFields() {
        assertTrue(StaffPermissions.canRegisterProducts(user("Ana Clara", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canRegisterProducts(user("ana  clara", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.canSeeCostFields(user("Ana Clara", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canSeeCostFields(user("Dayana", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canRegisterProducts(user("Dayana", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canRegisterProducts(user("Chefe", User.Role.GERENTE)));
    }

    @Test
    void privilegedNamesAreReservedSoNobodyCanRenameIntoThem() {
        assertTrue(StaffPermissions.isReservedName("Dayana"));
        assertTrue(StaffPermissions.isReservedName("João"));
        assertTrue(StaffPermissions.isReservedName("Ana Clara"));
        assertTrue(StaffPermissions.isReservedName("Yan"));
        assertTrue(StaffPermissions.isReservedName("  yAN "));
        assertFalse(StaffPermissions.isReservedName("Maria"));
        assertFalse(StaffPermissions.isReservedName("Yann"));
        assertFalse(StaffPermissions.isReservedName(null));
    }

    @Test
    void dayanaAndManagersEditSalePriceButOtherOperatorsDoNot() {
        assertTrue(StaffPermissions.canEditPrices(user("Dayana", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canEditPrices(user("Chefe", User.Role.GERENTE)));
        assertFalse(StaffPermissions.canEditPrices(user("Joao", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.canEditPrices(user("Ana Clara", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.canEditPrices(user("Ana Clara", User.Role.GERENTE)));
    }

    @Test
    void onlyAnaClaraIsViewOnly() {
        assertTrue(StaffPermissions.isViewOnly(user("Ana Clara", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.isViewOnly(user("Dayana", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.isViewOnly(null));
    }

    @Test
    void anaClaraIsLimitedToStockQuantityAndIsNotFullyReadOnly() {
        User anaClara = user("Ana Clara", User.Role.OPERADOR);
        assertTrue(StaffPermissions.isStockQuantityOnly(anaClara));
        assertFalse(StaffPermissions.isFullyReadOnly(anaClara));
        assertFalse(StaffPermissions.canEditProductData(anaClara));
        assertFalse(StaffPermissions.isStockQuantityOnly(user("Dayana", User.Role.OPERADOR)));
        assertTrue(StaffPermissions.canEditProductData(user("Maria", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.isStockQuantityOnly(null));
        assertFalse(StaffPermissions.isFullyReadOnly(null));
        assertFalse(StaffPermissions.canEditProductData(null));
    }

    @Test
    void onlyYanIsAdministrator() {
        assertTrue(StaffPermissions.isAdministrator(user("Yan", User.Role.GERENTE)));
        assertTrue(StaffPermissions.isAdministrator(user(" YAN ", User.Role.GERENTE)));
        assertFalse(StaffPermissions.isAdministrator(user("Chefe", User.Role.GERENTE)));
        assertFalse(StaffPermissions.isAdministrator(user("Dayana", User.Role.OPERADOR)));
        assertFalse(StaffPermissions.isAdministrator(user("Yan Carlos", User.Role.GERENTE)));
        assertFalse(StaffPermissions.isAdministrator(null));
    }
}
