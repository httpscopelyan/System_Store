package br.com.scopel.pfv.beta.controller.support;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public final class ChargeScreen {

    private final Label statusLabel;
    private final Button confirmButton;

    public ChargeScreen(Label statusLabel, Button confirmButton) {
        this.statusLabel = statusLabel;
        this.confirmButton = confirmButton;
    }

    public void hideConfirm() {
        confirmButton.setVisible(false);
        confirmButton.setManaged(false);
    }

    public void showEnded(String message) {
        showConfirmAs("Voltar à escolha (ENTER)");
        statusLabel.setText(message
                + " Nenhum valor foi registrado. Pressione ENTER para voltar à escolha do pagamento.");
    }

    public void showRegistrationFailure(String reason) {
        showConfirmAs("Gravar venda (ENTER)");
        statusLabel.setText("PAGAMENTO RECEBIDO, mas a venda não foi gravada (" + reason
                + "). Pressione ENTER para tentar gravar de novo. Não cobre o cliente outra vez.");
    }

    private void showConfirmAs(String text) {
        confirmButton.setText(text);
        confirmButton.setDisable(false);
        confirmButton.setVisible(true);
        confirmButton.setManaged(true);
        Platform.runLater(confirmButton::requestFocus);
    }
}
