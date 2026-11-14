package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public final class AdminDialogs {

    private AdminDialogs() {
    }

    public static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        return alert.showAndWait().filter(answer -> answer == ButtonType.YES).isPresent();
    }
}
