package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.service.SplitPaymentStatus;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class SplitPaymentView {

    private final Label totalLabel;
    private final Label paidLabel;
    private final Label remainingLabel;
    private final VBox partsBox;
    private final Consumer<Payment> onRemoveCashPart;

    private List<Payment> shownParts = List.of();
    private BigDecimal remainingAmount = BigDecimal.ZERO;

    public SplitPaymentView(Label totalLabel, Label paidLabel, Label remainingLabel, VBox partsBox,
                            Consumer<Payment> onRemoveCashPart) {
        this.totalLabel = totalLabel;
        this.paidLabel = paidLabel;
        this.remainingLabel = remainingLabel;
        this.partsBox = partsBox;
        this.onRemoveCashPart = onRemoveCashPart;
    }

    public void show(SplitPaymentStatus status) {
        shownParts = List.copyOf(status.parts());
        remainingAmount = status.remainingAmount();
        totalLabel.setText(MoneyFormat.format(status.total()));
        paidLabel.setText(MoneyFormat.format(status.paidAmount()));
        remainingLabel.setText(MoneyFormat.format(status.remainingAmount()));
        partsBox.getChildren().clear();
        if (shownParts.isEmpty()) {
            Label emptyLabel = new Label("Nenhuma parte registrada ainda.");
            emptyLabel.getStyleClass().add("win-label");
            partsBox.getChildren().add(emptyLabel);
            return;
        }
        shownParts.forEach(part -> partsBox.getChildren().add(rowOf(part)));
    }

    public BigDecimal remainingAmount() {
        return remainingAmount;
    }

    public boolean hasElectronicPart() {
        return shownParts.stream().anyMatch(part -> part.getMethod() != PaymentMethod.DINHEIRO);
    }

    public boolean hasCashPart() {
        return lastCashPart().isPresent();
    }

    public Optional<Payment> lastCashPart() {
        return shownParts.stream().filter(part -> part.getMethod() == PaymentMethod.DINHEIRO)
                .reduce((earlierPart, laterPart) -> laterPart);
    }

    public static String describe(PaymentMethod method, CardType cardType,
                                  BigDecimal received, BigDecimal change) {
        String description = SplitPartMethod.labelOf(method, cardType) + "   " + MoneyFormat.format(received);
        return change != null && change.signum() > 0
                ? description + "   (troco " + MoneyFormat.format(change) + ")" : description;
    }

    private HBox rowOf(Payment part) {
        Label descriptionLabel = new Label(describe(part.getMethod(), part.getCardType(),
                part.getAmountReceived(), part.getChange()));
        descriptionLabel.getStyleClass().add("split-part-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, descriptionLabel, spacer);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("split-part-row");
        if (part.getMethod() == PaymentMethod.DINHEIRO) {
            Button removeButton = new Button("Remover");
            removeButton.setFocusTraversable(false);
            removeButton.getStyleClass().add("split-part-remove");
            removeButton.setOnAction(event -> onRemoveCashPart.accept(part));
            row.getChildren().add(removeButton);
        }
        return row;
    }
}
