package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.service.cash.CashClosingService;
import br.com.scopel.pfv.beta.service.cash.CashSummary;
import br.com.scopel.pfv.beta.service.cash.ClosingCount;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public final class CashClosingPanel {

    public static final String MISSING_COUNT_MESSAGE =
            "Informe o valor contado na gaveta, PIX, Crédito, Débito e Extra (use 0 se não houve).";
    public static final String MISSING_EXTRA_NOTE_MESSAGE =
            "Informe a Observação do Extra: o valor contado é diferente do esperado.";

    private static final String EXTRA_NOTE_OPTIONAL_TEXT = "Observação do Extra";
    private static final String EXTRA_NOTE_REQUIRED_TEXT =
            "Observação do Extra (obrigatória: contado difere do esperado)";

    private final CashClosingLine drawerLine;
    private final CashClosingLine pixLine;
    private final CashClosingLine creditLine;
    private final CashClosingLine debitLine;
    private final CashClosingLine extraLine;
    private final Label extraNoteLabel;
    private final TextField extraNoteField;
    private final Label totalDifferenceLabel;
    private final Button closeButton;

    private String shownRegisterKey = "";
    private boolean editable;

    public CashClosingPanel(CashClosingLine drawerLine, CashClosingLine pixLine, CashClosingLine creditLine,
                            CashClosingLine debitLine, CashClosingLine extraLine, Label extraNoteLabel,
                            TextField extraNoteField, Label totalDifferenceLabel, Button closeButton) {
        this.drawerLine = drawerLine;
        this.pixLine = pixLine;
        this.creditLine = creditLine;
        this.debitLine = debitLine;
        this.extraLine = extraLine;
        this.extraNoteLabel = extraNoteLabel;
        this.extraNoteField = extraNoteField;
        this.totalDifferenceLabel = totalDifferenceLabel;
        this.closeButton = closeButton;
        MaxLengthInput.limit(extraNoteField, CashClosingService.MAX_EXTRA_NOTE_LENGTH);
        lines().forEach(line -> line.onCountedChanged(this::refreshDifferences));
    }

    public void show(CashRegister register, CashSummary summary) {
        boolean open = register != null && register.getStatus() == CashRegister.Status.OPEN;
        boolean closed = register != null && !open;
        String registerKey = register == null ? "" : register.getId() + ":" + register.getStatus();
        editable = open;
        drawerLine.showExpected(stored(closed, closed ? register.getExpectedAmount() : null, summary.finalBalance()));
        pixLine.showExpected(stored(closed, closed ? register.getExpectedPix() : null, summary.pix()));
        creditLine.showExpected(stored(closed, closed ? register.getExpectedCredit() : null, summary.credit()));
        debitLine.showExpected(stored(closed, closed ? register.getExpectedDebit() : null, summary.debit()));
        extraLine.showExpected(stored(closed, closed ? register.getExpectedExtra() : null, summary.extra()));
        if (!registerKey.equals(shownRegisterKey)) {
            shownRegisterKey = registerKey;
            showCounted(closed ? register : null);
        }
        lines().forEach(line -> line.setEditable(open));
        extraNoteField.setEditable(open);
        closeButton.setDisable(!open);
        refreshDifferences();
    }

    public Optional<ClosingCount> closingCount() {
        if (lines().stream().anyMatch(line -> line.countedAmount() == null)) {
            return Optional.empty();
        }
        return Optional.of(new ClosingCount(drawerLine.countedAmount(), pixLine.countedAmount(),
                creditLine.countedAmount(), debitLine.countedAmount(), extraLine.countedAmount(),
                extraNoteField.getText()));
    }

    public boolean isExtraNoteMissing() {
        return extraDiffers() && (extraNoteField.getText() == null || extraNoteField.getText().isBlank());
    }

    public void focusExtraNote() {
        extraNoteField.requestFocus();
    }

    private BigDecimal stored(boolean closed, BigDecimal storedAmount, BigDecimal calculatedAmount) {
        return closed && storedAmount != null ? storedAmount : calculatedAmount;
    }

    private void showCounted(CashRegister closedRegister) {
        drawerLine.showCounted(closedRegister == null ? null : closedRegister.getClosingAmount());
        pixLine.showCounted(closedRegister == null ? null : closedRegister.getCountedPix());
        creditLine.showCounted(closedRegister == null ? null : closedRegister.getCountedCredit());
        debitLine.showCounted(closedRegister == null ? null : closedRegister.getCountedDebit());
        extraLine.showCounted(closedRegister == null ? null : closedRegister.getCountedExtra());
        String extraNote = closedRegister == null ? null : closedRegister.getExtraNote();
        extraNoteField.setText(extraNote == null ? "" : extraNote);
    }

    private boolean extraDiffers() {
        BigDecimal extraDifference = extraLine.difference();
        return extraDifference != null && extraDifference.signum() != 0;
    }

    private void refreshDifferences() {
        BigDecimal totalDifference = BigDecimal.ZERO;
        boolean everyLineCounted = true;
        boolean anyLineCounted = false;
        boolean balanced = true;
        for (CashClosingLine line : lines()) {
            line.refreshDifference();
            BigDecimal difference = line.difference();
            if (difference == null) {
                everyLineCounted = false;
                continue;
            }
            anyLineCounted = true;
            totalDifference = totalDifference.add(difference);
            balanced &= difference.signum() == 0;
        }
        refreshExtraNoteLabel();
        showTotal(totalDifference, editable ? everyLineCounted : anyLineCounted, balanced);
    }

    private void refreshExtraNoteLabel() {
        boolean required = editable && extraDiffers();
        extraNoteLabel.setText(required ? EXTRA_NOTE_REQUIRED_TEXT : EXTRA_NOTE_OPTIONAL_TEXT);
        extraNoteLabel.getStyleClass().remove(CashClosingLine.DIFFERENCE_BAD_STYLE);
        if (required) {
            extraNoteLabel.getStyleClass().add(CashClosingLine.DIFFERENCE_BAD_STYLE);
        }
    }

    private void showTotal(BigDecimal totalDifference, boolean showable, boolean balanced) {
        totalDifferenceLabel.getStyleClass().removeAll(CashClosingLine.DIFFERENCE_BAD_STYLE,
                CashClosingLine.DIFFERENCE_OK_STYLE);
        if (!showable) {
            totalDifferenceLabel.setText(editable ? "Informe o contado de cada forma de pagamento." : "");
            return;
        }
        if (balanced) {
            totalDifferenceLabel.setText("Caixa confere: sem diferença.");
        } else {
            String verdict = totalDifference.signum() < 0 ? "FALTA "
                    : totalDifference.signum() > 0 ? "SOBRA " : "DIVERGÊNCIA ";
            totalDifferenceLabel.setText(verdict + MoneyFormat.format(totalDifference.abs())
                    + " — diferença total: " + MoneyFormat.format(totalDifference));
        }
        totalDifferenceLabel.getStyleClass().add(balanced ? CashClosingLine.DIFFERENCE_OK_STYLE
                : CashClosingLine.DIFFERENCE_BAD_STYLE);
    }

    private List<CashClosingLine> lines() {
        return List.of(drawerLine, pixLine, creditLine, debitLine, extraLine);
    }
}
