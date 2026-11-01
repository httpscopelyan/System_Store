package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.model.CardType;
import javafx.application.Platform;

import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class CardChargePoller {

    private static final long POLL_INTERVAL_SECONDS = 2;

    private final CardPaymentGateway gateway;
    private final Consumer<String> onMessage;
    private final Runnable onApproved;
    private final Consumer<String> onEnded;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "card-charge-poller");
        thread.setDaemon(true);
        return thread;
    });

    private volatile String chargeId;
    private volatile boolean finished;

    public CardChargePoller(CardPaymentGateway gateway, Consumer<String> onMessage, Runnable onApproved,
                            Consumer<String> onEnded) {
        this.gateway = gateway;
        this.onMessage = onMessage;
        this.onApproved = onApproved;
        this.onEnded = onEnded;
    }

    public void start(BigDecimal amount, CardType type) {
        executor.execute(() -> {
            try {
                chargeId = gateway.createCharge(amount, type).chargeId();
                post(onMessage, "Cobrança enviada à maquininha. Aguardando o cliente pagar...");
                executor.schedule(this::poll, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
            } catch (CardGatewayException failure) {
                end(failure.getMessage());
            }
        });
    }

    public void cancel(Runnable onCanceled, Consumer<String> onError) {
        executor.execute(() -> {
            if (finished) {
                return;
            }
            try {
                if (chargeId != null) {
                    gateway.cancel(chargeId);
                }
                finished = true;
                Platform.runLater(onCanceled);
                executor.shutdownNow();
            } catch (CardGatewayException failure) {
                post(onError, failure.getMessage() + " Cancele também na maquininha.");
            }
        });
    }

    public void stop() {
        finished = true;
        executor.shutdownNow();
    }

    private void poll() {
        if (finished) {
            return;
        }
        try {
            switch (gateway.getStatus(chargeId)) {
                case APPROVED -> {
                    finished = true;
                    Platform.runLater(onApproved);
                    executor.shutdown();
                    return;
                }
                case DECLINED -> {
                    end("Pagamento recusado pela maquininha.");
                    return;
                }
                case CANCELED -> {
                    end("Cobrança cancelada.");
                    return;
                }
                case PENDING -> {
                }
            }
        } catch (CardGatewayException failure) {
            post(onMessage, failure.getMessage() + " Tentando de novo...");
        }
        if (!finished) {
            executor.schedule(this::poll, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void end(String message) {
        finished = true;
        post(onEnded, message);
        executor.shutdown();
    }

    private void post(Consumer<String> target, String message) {
        Platform.runLater(() -> target.accept(message));
    }
}
