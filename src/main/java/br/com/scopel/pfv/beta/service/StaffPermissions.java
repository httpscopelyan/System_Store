package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class StaffPermissions {

    private static final Set<String> STOCK_QUANTITY_EDITORS = Set.of("dayana", "joao");
    private static final Set<String> PRICE_EDITORS = Set.of("dayana");
    private static final Set<String> VIEW_ONLY = Set.of("ana clara");

    private StaffPermissions() {
    }

    public static boolean canEditStockQuantity(User user) {
        return user != null && matchesAny(user, STOCK_QUANTITY_EDITORS);
    }

    public static boolean canEditPrices(User user) {
        return user != null && (user.getRole() == User.Role.GERENTE || matchesAny(user, PRICE_EDITORS));
    }

    public static boolean canRegisterProducts(User user) {
        return user != null && !isViewOnly(user);
    }

    public static boolean isViewOnly(User user) {
        return user != null && matchesAny(user, VIEW_ONLY);
    }

    public static boolean isReservedName(String name) {
        String normalized = normalize(name);
        return STOCK_QUANTITY_EDITORS.contains(normalized) || PRICE_EDITORS.contains(normalized)
                || VIEW_ONLY.contains(normalized);
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
