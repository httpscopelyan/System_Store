package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class StaffPermissions {

    private static final Set<String> STOCK_QUANTITY_EDITORS = Set.of("dayana", "joao", "ana clara");
    private static final Set<String> PRICE_EDITORS = Set.of("dayana");
    private static final Set<String> STOCK_QUANTITY_ONLY = Set.of("ana clara");
    private static final Set<String> ADMINISTRATORS = Set.of("yan");

    private StaffPermissions() {
    }

    public static boolean canEditStockQuantity(User user) {
        return user != null && matchesAny(user, STOCK_QUANTITY_EDITORS);
    }

    public static boolean canEditPrices(User user) {
        return user != null && !isStockQuantityOnly(user)
                && (user.getRole() == User.Role.GERENTE || matchesAny(user, PRICE_EDITORS));
    }

    public static boolean canRegisterProducts(User user) {
        return user != null && !isStockQuantityOnly(user);
    }

    public static boolean canEditProductData(User user) {
        return user != null && !isStockQuantityOnly(user);
    }

    public static boolean isStockQuantityOnly(User user) {
        return user != null && matchesAny(user, STOCK_QUANTITY_ONLY);
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
        return STOCK_QUANTITY_EDITORS.contains(normalized) || PRICE_EDITORS.contains(normalized)
                || STOCK_QUANTITY_ONLY.contains(normalized) || ADMINISTRATORS.contains(normalized);
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
