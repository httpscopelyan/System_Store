package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public final class BulkEditConfirmation {

    private BulkEditConfirmation() {
    }

    public static boolean ask(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText("Edição em lote");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
}
