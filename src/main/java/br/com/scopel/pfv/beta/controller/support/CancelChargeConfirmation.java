package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

public final class CancelChargeConfirmation {

    private final ButtonType keepWaiting = new ButtonType("Não, continuar aguardando", ButtonBar.ButtonData.CANCEL_CLOSE);
    private final ButtonType cancelCharge = new ButtonType("Sim, cancelar a cobrança", ButtonBar.ButtonData.OK_DONE);
    private final String message;
    private Alert alert;

    public CancelChargeConfirmation(String message) {
        this.message = message;
    }

    public boolean ask() {
        alert = new Alert(Alert.AlertType.CONFIRMATION, message, cancelCharge, keepWaiting);
        alert.setHeaderText("Cancelar esta cobrança?");
        Button safe = (Button) alert.getDialogPane().lookupButton(keepWaiting);
        Button destructive = (Button) alert.getDialogPane().lookupButton(cancelCharge);
        safe.setDefaultButton(true);
        destructive.setDefaultButton(false);
        boolean confirmed = alert.showAndWait().filter(cancelCharge::equals).isPresent();
        alert = null;
        return confirmed;
    }

    public void dismiss() {
        Alert open = alert;
        if (open != null) {
            open.setResult(keepWaiting);
            open.close();
        }
    }
}
