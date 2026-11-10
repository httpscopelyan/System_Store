package br.com.scopel.pfv.beta.controller.support;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public final class SplitPaymentScreen {

    private final Node methodPanel;
    private final Node amountPanel;
    private final Node manualBox;
    private final Node pixBox;
    private final Button confirmButton;
    private final Label failureLabel;
    private final TextField partAmountField;
    private final Node focusRoot;

    public SplitPaymentScreen(Node methodPanel, Node amountPanel, Node manualBox, Node pixBox,
                              Button confirmButton, Label failureLabel, TextField partAmountField, Node focusRoot) {
        this.methodPanel = methodPanel;
        this.amountPanel = amountPanel;
        this.manualBox = manualBox;
        this.pixBox = pixBox;
        this.confirmButton = confirmButton;
        this.failureLabel = failureLabel;
        this.partAmountField = partAmountField;
        this.focusRoot = focusRoot;
    }

    public void show(SplitStep step, SplitPartMethod chosenMethod, boolean machineIntegrated, boolean manualPixShown) {
        boolean enteringAmount = step == SplitStep.ENTERING_AMOUNT;
        boolean manualCardCharge = chosenMethod != null && chosenMethod.isElectronic()
                && chosenMethod != SplitPartMethod.PIX && !machineIntegrated;
        setShown(methodPanel, step == SplitStep.CHOOSING_METHOD);
        setShown(amountPanel, enteringAmount);
        setShown(manualBox, enteringAmount && manualCardCharge);
        setShown(pixBox, enteringAmount && manualPixShown);
        setShown(confirmButton, enteringAmount || step == SplitStep.REGISTRATION_FAILED);
        confirmButton.setText(step == SplitStep.REGISTRATION_FAILED ? "Gravar parte (ENTER)" : "Confirmar parte (Enter)");
        Platform.runLater(() -> {
            if (enteringAmount) {
                partAmountField.requestFocus();
                partAmountField.selectAll();
            } else {
                focusRoot.requestFocus();
            }
        });
    }

    public void showRegistrationFailure(String reason) {
        failureLabel.setText("PAGAMENTO RECEBIDO NA MAQUININHA, mas a parte não foi gravada (" + reason
                + "). Pressione ENTER para gravar de novo. NÃO cobre o cliente outra vez;"
                + " se persistir, chame o gerente.");
        setShown(failureLabel, true);
    }

    public void hideFailure() {
        setShown(failureLabel, false);
    }

    public void showPix(boolean shown) {
        setShown(pixBox, shown);
    }

    private void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }
}
