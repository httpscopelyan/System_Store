package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;

import javafx.scene.Node;

import java.math.BigDecimal;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class SplitChargeSession {

    private final CardPaymentGateway gateway;
    private final Navigator navigator;
    private final Consumer<String> statusMessage;
    private final ChargeCancellation cancellation = new ChargeCancellation();

    private CardChargePoller poller;

    public SplitChargeSession(CardPaymentGateway gateway, Navigator navigator, Consumer<String> statusMessage) {
        this.gateway = gateway;
        this.navigator = navigator;
        this.statusMessage = statusMessage;
    }

    public void start(SplitPartMethod method, BigDecimal amount, Runnable onApproved, Consumer<String> onStopped) {
        navigator.lock("Cobrança em andamento na maquininha. Use ESC para cancelar antes de sair desta tela.");
        statusMessage.accept("Enviando a cobrança para a maquininha...");
        poller = new CardChargePoller(gateway, statusMessage, onApproved, onStopped);
        poller.start(() -> method.createChargeOn(gateway, amount));
    }

    public void requestCancel(BooleanSupplier alreadyClosed, Runnable onCanceled) {
        cancellation.request(poller, alreadyClosed, statusMessage, onCanceled);
    }

    public void release() {
        poller = null;
        navigator.unlock();
    }

    public void abandonWhenLeft(Node screenRoot) {
        screenRoot.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                abandon();
            }
        });
    }

    private void abandon() {
        if (poller != null) {
            poller.stop();
            release();
        }
    }
}
