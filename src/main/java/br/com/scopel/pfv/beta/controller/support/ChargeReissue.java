package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.DirectCharge;
import br.com.scopel.pfv.beta.service.ReceiptService;
import javafx.scene.Node;
import javafx.scene.control.Label;

public final class ChargeReissue {

    private final ReceiptService receiptService;
    private final Node button;
    private final Label statusLabel;

    private DirectCharge lastCharge;

    public ChargeReissue(ReceiptService receiptService, Node button, Label statusLabel) {
        this.receiptService = receiptService;
        this.button = button;
        this.statusLabel = statusLabel;
        hide();
    }

    public void offer(DirectCharge charge) {
        lastCharge = charge;
        setVisible(charge != null);
    }

    public void hide() {
        lastCharge = null;
        setVisible(false);
    }

    public void print() {
        DirectCharge charge = lastCharge;
        if (charge == null) {
            return;
        }
        statusLabel.setText("Enviando a 2ª via para a impressora...");
        BackgroundPrinting.run(() -> receiptService.printDirectChargeReceiptCopy(charge),
                message -> statusLabel.setText("Falha ao imprimir a 2ª via: " + message));
    }

    private void setVisible(boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }
}
