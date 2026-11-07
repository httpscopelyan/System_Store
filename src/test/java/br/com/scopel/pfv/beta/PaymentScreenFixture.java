package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.ChargeCancellation;
import br.com.scopel.pfv.beta.exception.CardGatewayException;
import br.com.scopel.pfv.beta.hardware.CardChargeResult;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.hardware.CardPaymentStatus;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.mockito.Mockito.mock;

final class PaymentScreenFixture {

    static final class ScriptedGateway implements CardPaymentGateway {
        final AtomicInteger charges = new AtomicInteger();
        final AtomicInteger cancels = new AtomicInteger();
        private final boolean integrated;
        private final Deque<CardPaymentStatus> statuses = new ArrayDeque<>();
        private CardPaymentStatus last = CardPaymentStatus.PENDING;
        private volatile boolean cancelFails;

        ScriptedGateway(boolean integrated) {
            this.integrated = integrated;
        }

        synchronized ScriptedGateway script(CardPaymentStatus... sequence) {
            statuses.clear();
            statuses.addAll(java.util.List.of(sequence));
            return this;
        }

        ScriptedGateway cancelFails() {
            cancelFails = true;
            return this;
        }

        @Override public boolean isIntegrated() {
            return integrated;
        }

        @Override public CardChargeResult createCharge(BigDecimal amount, CardType type) {
            charges.incrementAndGet();
            return new CardChargeResult("order-" + charges.get(), CardPaymentStatus.PENDING);
        }

        @Override public CardChargeResult createPixCharge(BigDecimal amount) {
            return createCharge(amount, null);
        }

        @Override public synchronized CardPaymentStatus getStatus(String chargeId) {
            if (!statuses.isEmpty()) {
                last = statuses.poll();
            }
            return last;
        }

        @Override public void cancel(String chargeId) {
            cancels.incrementAndGet();
            if (cancelFails) {
                throw new CardGatewayException("A ordem já foi processada.");
            }
        }
    }

    final SaleService saleService = mock(SaleService.class);
    final ReceiptService receiptService = mock(ReceiptService.class);
    final Navigator navigator = mock(Navigator.class);
    final UserSession session = new UserSession();
    final Sale sale = new Sale();

    Object controller;
    Parent root;
    private Map<String, Object> namespace;

    PaymentScreenFixture() {
        sale.setTotal(new BigDecimal("25.90"));
        session.setVendaAtual(sale);
        session.setCardTypeSelecionado(CardType.DEBITO);
    }

    <T> T load(String fxml, Function<PaymentScreenFixture, T> controllerFactory) throws Exception {
        return onFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + fxml));
            T created = controllerFactory.apply(this);
            loader.setControllerFactory(type -> created);
            root = loader.load();
            namespace = loader.getNamespace();
            controller = created;
            return created;
        });
    }

    void answerCancellation(boolean confirmed) throws Exception {
        ChargeCancellation scripted = new ChargeCancellation(message -> new ChargeCancellation.Confirmation() {
            @Override public boolean ask() { return confirmed; }
            @Override public void dismiss() { }
        });
        Field field = controller.getClass().getDeclaredField("cancellation");
        field.setAccessible(true);
        field.set(controller, scripted);
    }

    <T> T onFx(Callable<T> action) throws Exception {
        if (javafx.application.Platform.isFxApplicationThread()) {
            return action.call();
        }
        CompletableFuture<T> done = new CompletableFuture<>();
        javafx.application.Platform.runLater(() -> {
            try {
                done.complete(action.call());
            } catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        });
        return done.get(20, TimeUnit.SECONDS);
    }

    void onFx(Runnable action) throws Exception {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    String status() throws Exception {
        return onFx(() -> ((Label) namespace.get("statusLabel")).getText());
    }

    Button button(String fxId) {
        return (Button) namespace.get(fxId);
    }

    Node node(String fxId) {
        return (Node) namespace.get(fxId);
    }

    void press(KeyCode code) throws Exception {
        onFx(() -> root.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false)));
    }

    boolean waitUntil(BooleanSupplier condition, int seconds) throws InterruptedException {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < limit) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    boolean statusContains(String text, int seconds) throws Exception {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < limit) {
            if (status().contains(text)) {
                return true;
            }
            Thread.sleep(100);
        }
        return status().contains(text);
    }
}
