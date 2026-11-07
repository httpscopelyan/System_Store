package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.hardware.CardChargeResult;
import br.com.scopel.pfv.beta.hardware.CardPaymentStatus;
import javafx.application.Platform;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private CardPaymentStatus lastEndingStatus;

    public CardChargePoller(CardPaymentGateway gateway, Consumer<String> onMessage, Runnable onApproved,
                            Consumer<String> onEnded) {
        this.gateway = gateway;
        this.onMessage = onMessage;
        this.onApproved = onApproved;
        this.onEnded = onEnded;
    }

    public void start(Supplier<CardChargeResult> chargeCreator) {
        executor.execute(() -> {
            try {
                chargeId = chargeCreator.get().chargeId();
                post(onMessage, "Cobrança enviada à maquininha. Aguardando o cliente pagar...");
                executor.schedule(this::poll, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
            } catch (CardGatewayException failure) {
                end(failure.getMessage());
            }
        });
    }

    public void cancel(Runnable onCanceled, Consumer<String> onError) {
        try {
            executor.execute(() -> cancelNow(onCanceled, onError));
        } catch (RejectedExecutionException alreadyFinished) {
            post(onError, "A cobrança já foi encerrada.");
        }
    }

    private void cancelNow(Runnable onCanceled, Consumer<String> onError) {
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
            if (alreadyPaid()) {
                return;
            }
            post(onError, failure.getMessage() + " Cancele também na maquininha.");
        }
    }

    private boolean alreadyPaid() {
        try {
            if (chargeId != null && gateway.getStatus(chargeId) == CardPaymentStatus.APPROVED) {
                finished = true;
                Platform.runLater(onApproved);
                executor.shutdown();
                return true;
            }
        } catch (CardGatewayException ignored) {
            return false;
        }
        return false;
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
                    if (confirmedEnd(CardPaymentStatus.DECLINED)) {
                        end("Pagamento recusado pela maquininha.");
                        return;
                    }
                }
                case CANCELED -> {
                    if (confirmedEnd(CardPaymentStatus.CANCELED)) {
                        end("Cobrança cancelada na maquininha.");
                        return;
                    }
                }
                case PENDING -> lastEndingStatus = null;
            }
        } catch (CardGatewayException failure) {
            post(onMessage, failure.getMessage() + " Tentando de novo...");
        }
        if (!finished) {
            executor.schedule(this::poll, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private boolean confirmedEnd(CardPaymentStatus status) {
        boolean confirmed = lastEndingStatus == status;
        lastEndingStatus = status;
        return confirmed;
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
