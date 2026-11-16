package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.service.refund.RefundPlan;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.Optional;

public final class SaleRefundDialog {

    public record Input(String reason, boolean manualRefundConfirmed) {
    }

    private static final double DIALOG_WIDTH = 520;

    private SaleRefundDialog() {
    }

    public static Optional<Input> ask(Window owner, RefundPlan plan) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Reembolsar venda");
        dialog.setHeaderText("Venda #" + plan.saleId() + "  —  Total " + MoneyFormat.format(plan.total()));
        ButtonType confirm = new ButtonType("Reembolsar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(confirm, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(12));
        content.setPrefWidth(DIALOG_WIDTH);
        if (plan.cashToReturn().compareTo(BigDecimal.ZERO) > 0) {
            Label cash = label("DEVOLVER AO CLIENTE EM DINHEIRO: " + MoneyFormat.format(plan.cashToReturn()),
                    "total-value");
            cash.setStyle("-fx-font-size: 22px; -fx-text-fill: #8a1c1c;");
            content.getChildren().addAll(cash,
                    label("Esse valor sai do caixa e a venda deixa de contar na movimentação.", "win-label"));
        }
        CheckBox manualConfirmed = new CheckBox("Já devolvi o valor no aplicativo do Mercado Pago");
        for (RefundPlan.ElectronicPart part : plan.electronicParts()) {
            content.getChildren().add(label(describe(part), "win-label-bold"));
        }
        if (plan.needsManualConfirmation()) {
            content.getChildren().add(manualConfirmed);
        }
        TextField reason = new TextField();
        reason.setPromptText("Motivo do reembolso (obrigatório)");
        content.getChildren().addAll(label("Motivo:", "win-label-bold"), reason);
        dialog.getDialogPane().setContent(content);
        reason.requestFocus();
        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != confirm) {
            return Optional.empty();
        }
        return Optional.of(new Input(reason.getText(), manualConfirmed.isSelected()));
    }

    private static String describe(RefundPlan.ElectronicPart part) {
        String method = part.method() == PaymentMethod.PIX ? "PIX" : "Cartão"
                + (part.cardType() == null ? "" : " " + part.cardType());
        String how = part.alreadyRefunded() ? "já reembolsado"
                : part.automatic() ? "devolvido automaticamente pela maquininha"
                : "devolver pelo aplicativo do Mercado Pago";
        return method + " " + MoneyFormat.format(part.amount()) + " — " + how;
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        return label;
    }
}
