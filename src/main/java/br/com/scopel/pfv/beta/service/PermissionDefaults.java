package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.model.User;

import java.util.EnumSet;
import java.util.Set;

public final class PermissionDefaults {

    private static final Set<String> STOCK_QUANTITY_EDITORS = Set.of("dayana", "joao", "ana clara");
    private static final Set<String> PRICE_EDITORS = Set.of("dayana");
    private static final String QUANTITY_ONLY = "ana clara";

    private PermissionDefaults() {
    }

    public static Set<Permission> forAccount(String login, String name, User.Role role) {
        if (role == User.Role.DEV) {
            return EnumSet.allOf(Permission.class);
        }
        boolean quantityOnly = isNamed(login, name, Set.of(QUANTITY_ONLY));
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        permissions.add(Permission.REGISTER_PRODUCTS);
        if (!quantityOnly) {
            permissions.add(Permission.SEE_COST_FIELDS);
            permissions.add(Permission.EDIT_PRODUCT_DATA);
            if (role == User.Role.GERENTE || isNamed(login, name, PRICE_EDITORS)) {
                permissions.add(Permission.EDIT_PRICES);
            }
        }
        if (isNamed(login, name, STOCK_QUANTITY_EDITORS)) {
            permissions.add(Permission.EDIT_STOCK_QUANTITY);
        }
        return permissions;
    }

    private static boolean isNamed(String login, String name, Set<String> names) {
        return names.contains(StaffPermissions.normalize(login)) || names.contains(StaffPermissions.normalize(name));
    }
}
