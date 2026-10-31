package br.com.scopel.pfv.beta.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationHistoryTests {

    @Test
    void emptyHistoryCannotGoBack_RN30() {
        NavigationHistory history = new NavigationHistory();
        assertFalse(history.canGoBack());
        assertTrue(history.back().isEmpty());
    }

    @Test
    void backReturnsPagesInReverseOrder_RN30() {
        NavigationHistory history = new NavigationHistory();
        history.push(Page.INICIO);
        history.push(Page.ESTOQUE);
        assertEquals(Page.ESTOQUE, history.back().orElseThrow());
        assertEquals(Page.INICIO, history.back().orElseThrow());
        assertFalse(history.canGoBack());
    }

    @Test
    void consecutiveDuplicatePagesAreNotStored_RN30() {
        NavigationHistory history = new NavigationHistory();
        history.push(Page.ESTOQUE);
        history.push(Page.ESTOQUE);
        assertEquals(Page.ESTOQUE, history.back().orElseThrow());
        assertFalse(history.canGoBack());
    }

    @Test
    void paymentFlowPagesAreNeverStored_RN30() {
        NavigationHistory history = new NavigationHistory();
        history.push(Page.PAGAMENTO);
        history.push(Page.PAGAMENTO_DINHEIRO);
        history.push(Page.PAGAMENTO_CARTAO);
        history.push(Page.PAGAMENTO_ESPERA);
        history.push(Page.PAGAMENTO_PIX);
        history.push(Page.VENDA_FINALIZADA);
        assertFalse(history.canGoBack());
    }

    @Test
    void historyIsBoundedToTenEntries_RN30() {
        NavigationHistory history = new NavigationHistory();
        Page[] alternating = {Page.ESTOQUE, Page.CLIENTES};
        for (int index = 0; index < 25; index++) {
            history.push(alternating[index % 2]);
        }
        int stored = 0;
        while (history.back().isPresent()) {
            stored++;
        }
        assertEquals(10, stored);
    }

    @Test
    void clearRemovesEverything_RN30() {
        NavigationHistory history = new NavigationHistory();
        history.push(Page.ESTOQUE);
        history.clear();
        assertFalse(history.canGoBack());
    }

    @Test
    void nullPageIsIgnored_RN30() {
        NavigationHistory history = new NavigationHistory();
        history.push(null);
        assertFalse(history.canGoBack());
    }
}
