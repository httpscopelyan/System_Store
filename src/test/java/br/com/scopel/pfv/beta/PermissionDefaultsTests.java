package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.PermissionDefaults;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PermissionDefaultsTests {

    @Test
    void anaClaraRegistersProductsAndEditsQuantityOnly() {
        assertEquals(EnumSet.of(Permission.REGISTER_PRODUCTS, Permission.EDIT_STOCK_QUANTITY),
                PermissionDefaults.forAccount("Ana Clara", "Ana Clara", User.Role.OPERADOR));
    }

    @Test
    void dayanaHasEverything() {
        assertEquals(EnumSet.allOf(Permission.class),
                PermissionDefaults.forAccount("Dayana", "Dayana", User.Role.OPERADOR));
    }

    @Test
    void joaoEditsQuantityButNotPrices() {
        Set<Permission> joao = PermissionDefaults.forAccount("João", "João", User.Role.OPERADOR);
        assertEquals(EnumSet.of(Permission.REGISTER_PRODUCTS, Permission.SEE_COST_FIELDS,
                Permission.EDIT_PRODUCT_DATA, Permission.EDIT_STOCK_QUANTITY), joao);
    }

    @Test
    void managerEditsPricesButNotStockQuantity() {
        assertEquals(EnumSet.of(Permission.REGISTER_PRODUCTS, Permission.SEE_COST_FIELDS,
                        Permission.EDIT_PRODUCT_DATA, Permission.EDIT_PRICES),
                PermissionDefaults.forAccount("Chefe", "Chefe", User.Role.GERENTE));
    }

    @Test
    void devHasEverythingAndNewOperatorGetsBasics() {
        assertEquals(EnumSet.allOf(Permission.class), PermissionDefaults.forAccount("Yan", "Yan", User.Role.DEV));
        assertEquals(EnumSet.of(Permission.REGISTER_PRODUCTS, Permission.SEE_COST_FIELDS, Permission.EDIT_PRODUCT_DATA),
                PermissionDefaults.forAccount("Maria", "Maria", User.Role.OPERADOR));
    }
}
