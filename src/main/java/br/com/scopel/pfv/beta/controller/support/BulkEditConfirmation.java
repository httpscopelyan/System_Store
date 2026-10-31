package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public final class BulkEditConfirmation {

    private BulkEditConfirmation() {
    }

    public static boolean ask(String message) {
        return ask("Edição em lote", message);
    }

    public static boolean ask(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(header);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
}
