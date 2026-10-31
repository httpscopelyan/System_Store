package br.com.scopel.pfv.beta.config;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public class NavigationHistory {

    private static final int MAX_ENTRIES = 10;

    private static final Set<Page> PAYMENT_FLOW = EnumSet.of(
            Page.PAGAMENTO,
            Page.PAGAMENTO_DINHEIRO,
            Page.PAGAMENTO_CARTAO,
            Page.PAGAMENTO_ESPERA,
            Page.PAGAMENTO_PIX,
            Page.VENDA_FINALIZADA);

    private final Deque<Page> entries = new ArrayDeque<>();

    public void push(Page page) {
        if (page == null || PAYMENT_FLOW.contains(page)) {
            return;
        }
        if (page == entries.peekFirst()) {
            return;
        }
        entries.addFirst(page);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    public Optional<Page> back() {
        return Optional.ofNullable(entries.pollFirst());
    }

    public boolean canGoBack() {
        return !entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }
}
