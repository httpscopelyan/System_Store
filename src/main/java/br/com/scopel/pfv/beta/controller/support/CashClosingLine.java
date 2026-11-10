package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CashClosingLine {

    public static final String DIFFERENCE_BAD_STYLE = "diff-bad";
    public static final String DIFFERENCE_OK_STYLE = "diff-ok";

    private final TextField expectedField;
    private final TextField countedField;
    private final Label differenceLabel;

    private BigDecimal expectedAmount = BigDecimal.ZERO;

    public CashClosingLine(TextField expectedField, TextField countedField, Label differenceLabel) {
        this.expectedField = expectedField;
        this.countedField = countedField;
        this.differenceLabel = differenceLabel;
    }

    public void onCountedChanged(Runnable listener) {
        countedField.textProperty().addListener((observable, previous, current) -> listener.run());
    }

    public void showExpected(BigDecimal amount) {
        expectedAmount = amount == null ? BigDecimal.ZERO : amount;
        expectedField.setText(MoneyFormat.format(expectedAmount));
    }

    public void showCounted(BigDecimal amount) {
        countedField.setText(amount == null ? ""
                : amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ','));
    }

    public void setEditable(boolean editable) {
        countedField.setEditable(editable);
    }

    public BigDecimal countedAmount() {
        return DecimalInput.parseOrNull(countedField.getText());
    }

    public BigDecimal difference() {
        BigDecimal countedAmount = countedAmount();
        return countedAmount == null ? null : countedAmount.subtract(expectedAmount);
    }

    public void refreshDifference() {
        BigDecimal difference = difference();
        differenceLabel.getStyleClass().removeAll(DIFFERENCE_BAD_STYLE, DIFFERENCE_OK_STYLE);
        if (difference == null) {
            differenceLabel.setText("");
            return;
        }
        differenceLabel.setText("Diferença: " + MoneyFormat.format(difference));
        differenceLabel.getStyleClass().add(difference.signum() != 0 ? DIFFERENCE_BAD_STYLE : DIFFERENCE_OK_STYLE);
    }
}
