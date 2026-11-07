package br.com.scopel.pfv.beta.controller.support;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ChargeCancellation {

    public interface Confirmation {
        boolean ask();

        void dismiss();
    }

    private static final String PAID_WARNING =
            "Se o cliente já pagou na maquininha, NÃO cancele. A cobrança será cancelada na maquininha.";

    private final Function<String, Confirmation> confirmationFactory;
    private Confirmation confirmation;

    public ChargeCancellation() {
        this(message -> {
            CancelChargeConfirmation dialog = new CancelChargeConfirmation(message);
            return new Confirmation() {
                @Override public boolean ask() { return dialog.ask(); }
                @Override public void dismiss() { dialog.dismiss(); }
            };
        });
    }

    public ChargeCancellation(Function<String, Confirmation> confirmationFactory) {
        this.confirmationFactory = confirmationFactory;
    }

    public void request(CardChargePoller poller, BooleanSupplier alreadyClosed, Consumer<String> status,
                        Runnable onCanceled) {
        confirmation = confirmationFactory.apply(poller == null
                ? "A venda volta para a escolha da forma de pagamento." : PAID_WARNING);
        boolean confirmed = confirmation.ask();
        confirmation = null;
        if (!confirmed || alreadyClosed.getAsBoolean()) {
            return;
        }
        if (poller == null) {
            onCanceled.run();
            return;
        }
        status.accept("Cancelando a cobrança na maquininha...");
        poller.cancel(onCanceled, status);
    }

    public void dismiss() {
        Confirmation open = confirmation;
        if (open != null) {
            open.dismiss();
        }
    }
}
