package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.model.User;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class StaffPermissions {

    private static final Set<String> ADMINISTRATORS = Set.of("yan");
    private static final Set<String> RESERVED_NAMES = Set.of("dayana", "joao", "ana clara");

    private StaffPermissions() {
    }

    public static boolean canEditStockQuantity(User user) {
        return has(user, Permission.EDIT_STOCK_QUANTITY);
    }

    public static boolean canEditPrices(User user) {
        return has(user, Permission.EDIT_PRICES);
    }

    public static boolean canRegisterProducts(User user) {
        return has(user, Permission.REGISTER_PRODUCTS);
    }

    public static boolean canEditProductData(User user) {
        return has(user, Permission.EDIT_PRODUCT_DATA);
    }

    public static boolean canSeeCostFields(User user) {
        return has(user, Permission.SEE_COST_FIELDS);
    }

    public static boolean isStockQuantityOnly(User user) {
        return user != null && !canEditProductData(user) && canEditStockQuantity(user);
    }

    public static boolean isFullyReadOnly(User user) {
        return isViewOnly(user) && !canEditStockQuantity(user);
    }

    public static boolean isViewOnly(User user) {
        return user != null && !canEditProductData(user);
    }

    public static boolean isAdministrator(User user) {
        return user != null && matchesAny(user, ADMINISTRATORS);
    }

    public static boolean isReservedName(String name) {
        String normalized = normalize(name);
        return RESERVED_NAMES.contains(normalized) || ADMINISTRATORS.contains(normalized);
    }

    private static boolean has(User user, Permission permission) {
        return user != null && user.hasPermission(permission);
    }

    private static boolean matchesAny(User user, Set<String> names) {
        return names.contains(normalize(user.getLogin())) || names.contains(normalize(user.getName()));
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
