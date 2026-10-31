package br.com.scopel.pfv.beta.controller.support;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.Optional;

public final class RectificationDialog {

    public record Input(BigDecimal correctedAmount, String reason) {
    }

    private RectificationDialog() {
    }

    public static Optional<Input> ask(Window owner, String description) {
        Dialog<Input> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Retificar movimentação");
        dialog.setHeaderText(description);

        TextField amountField = new TextField();
        amountField.setPromptText("Valor correto (ex: 10,00)");
        TextField reasonField = new TextField();
        reasonField.setPromptText("Motivo da retificação");

        VBox content = new VBox(8, new Label("Valor correto"), amountField, new Label("Motivo da retificação"),
                reasonField);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            BigDecimal amount = DecimalInput.parseOrNull(amountField.getText());
            return amount == null ? null : new Input(amount, reasonField.getText());
        });
        return dialog.showAndWait();
    }
}
