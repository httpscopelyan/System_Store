package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;

import java.util.Set;

public final class SharedCashRegisterMembers {

    private static final Set<String> SHARED_REGISTER_NAMES = Set.of("dayana", "ana clara");

    private SharedCashRegisterMembers() {
    }

    public static boolean includes(User user) {
        return user != null && (includesName(user.getLogin()) || includesName(user.getName()));
    }

    public static boolean includesName(String name) {
        return SHARED_REGISTER_NAMES.contains(StaffPermissions.normalize(name));
    }
}
