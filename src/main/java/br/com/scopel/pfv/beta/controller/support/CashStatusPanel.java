package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.CashRegister;
import javafx.scene.control.Label;

import java.time.format.DateTimeFormatter;

public final class CashStatusPanel {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final Label statusLabel;
    private final Label openingInfoLabel;
    private final Label closingInfoLabel;
    private final Label periodLabel;

    public CashStatusPanel(Label statusLabel, Label openingInfoLabel, Label closingInfoLabel, Label periodLabel) {
        this.statusLabel = statusLabel;
        this.openingInfoLabel = openingInfoLabel;
        this.closingInfoLabel = closingInfoLabel;
        this.periodLabel = periodLabel;
    }

    public void show(CashRegister register) {
        boolean open = register.getStatus() == CashRegister.Status.OPEN;
        statusLabel.setText(open ? "ABERTO" : "FECHADO");
        statusLabel.getStyleClass().setAll(open ? "status-open" : "status-closed");
        openingInfoLabel.setText(DATE_TIME.format(register.getOpenedAt())
                + (register.getOperator() != null ? "\nAbertura por: " + register.getOperator().getName().toUpperCase() : ""));
        closingInfoLabel.setText(register.getClosedAt() == null ? "-"
                : DATE_TIME.format(register.getClosedAt()) + "\nDiferença: "
                + MoneyFormat.format(register.totalDifference()));
        periodLabel.setText("Movimentação do Caixa - Abertura: " + DATE_TIME.format(register.getOpenedAt()));
    }

    public void showEmpty() {
        statusLabel.setText("FECHADO");
        statusLabel.getStyleClass().setAll("status-closed");
        openingInfoLabel.setText("-");
        closingInfoLabel.setText("-");
        periodLabel.setText("Movimentação do Caixa");
    }
}
