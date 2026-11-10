package br.com.scopel.pfv.beta.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorPageBackHandlerTests {

    @Test
    void pageWithABackHandlerCanGoBackEvenWithAnEmptyHistory() {
        Navigator navigator = new Navigator(null, null);

        assertFalse(navigator.canGoBack());
        navigator.setPageBackHandler(() -> { });

        assertTrue(navigator.canGoBack());
    }

    @Test
    void backRunsThePageHandlerInsteadOfTheHistory() {
        Navigator navigator = new Navigator(null, null);
        AtomicInteger handlerRuns = new AtomicInteger();
        navigator.setPageBackHandler(handlerRuns::incrementAndGet);

        navigator.back();

        assertEquals(1, handlerRuns.get());
    }

    @Test
    void lockedNavigatorRefusesBackEvenWhenThePageHasAHandler() {
        Navigator navigator = new Navigator(null, null);
        List<String> notices = new ArrayList<>();
        navigator.setNotice(notices::add);
        AtomicInteger handlerRuns = new AtomicInteger();
        navigator.setPageBackHandler(handlerRuns::incrementAndGet);
        navigator.lock("Cobrança em andamento");

        navigator.back();

        assertEquals(0, handlerRuns.get());
        assertEquals(List.of("Cobrança em andamento"), notices);
    }

    @Test
    void bindingTheShellAgainDropsTheStalePageHandler() {
        Navigator navigator = new Navigator(null, null);
        navigator.setPageBackHandler(() -> { });

        navigator.bind(null);

        assertFalse(navigator.canGoBack());
    }
}
