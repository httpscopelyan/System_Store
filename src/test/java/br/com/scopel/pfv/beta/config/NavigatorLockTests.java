package br.com.scopel.pfv.beta.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorLockTests {

    @Test
    void lockedNavigatorRefusesToLeaveAndExplainsWhy() {
        Navigator navigator = new Navigator(null, null);
        List<String> notices = new ArrayList<>();
        navigator.setNotice(notices::add);

        navigator.lock("Cobrança em andamento");
        navigator.navigate(Page.CAIXA);
        navigator.back();

        assertTrue(navigator.isLocked());
        assertEquals(List.of("Cobrança em andamento", "Cobrança em andamento"), notices);
    }

    @Test
    void unlockReleasesTheNavigator() {
        Navigator navigator = new Navigator(null, null);
        navigator.lock("x");
        navigator.unlock();

        assertFalse(navigator.isLocked());
    }
}
