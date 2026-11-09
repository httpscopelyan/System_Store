package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.CashClosingFlow;
import br.com.scopel.pfv.beta.controller.support.DecimalInput;
import br.com.scopel.pfv.beta.controller.support.MoneyFormat;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.service.cash.CashSummary;
import br.com.scopel.pfv.beta.service.cash.ClosingCount;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.function.Consumer;

@Component
public class CashClosingController {

    private static final String BAD = "diff-bad";
    private static final String OK = "diff-ok";

    private final UserSession userSession;

    private CashClosingFlow flow;
    private Consumer<String> onFinished = message -> { };
    private String shownKey = "";
    private boolean editable;

    @FXML private TextField expCash;
    @FXML private TextField expPix;
    @FXML private TextField expCredit;
    @FXML private TextField expDebit;
    @FXML private TextField cntCash;
    @FXML private TextField cntPix;
    @FXML private TextField cntCredit;
    @FXML private TextField cntDebit;
    @FXML private Label difCash;
    @FXML private Label difPix;
    @FXML private Label difCredit;
    @FXML private Label difDebit;
    @FXML private Label totalDifferenceLabel;
    @FXML private Button closeButton;

    public CashClosingController(UserSession userSession) {
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        for (TextField field : new TextField[]{cntCash, cntPix, cntCredit, cntDebit}) {
            field.textProperty().addListener((observable, previous, current) -> refreshDifferences());
        }
    }

    public void bind(CashClosingFlow closingFlow, Consumer<String> finished) {
        this.flow = closingFlow;
        this.onFinished = finished;
    }

    public void show(CashRegister register, CashSummary summary) {
        boolean open = register != null && register.getStatus() == CashRegister.Status.OPEN;
        String key = register == null ? "" : register.getId() + ":" + register.getStatus();
        editable = open;
        if (!key.equals(shownKey)) {
            shownKey = key;
            fillCounted(register, open);
        }
        fillExpected(register, summary, open);
        for (TextField field : new TextField[]{cntCash, cntPix, cntCredit, cntDebit}) {
            field.setEditable(open);
        }
        closeButton.setDisable(!open);
        refreshDifferences();
    }

    @FXML
    public void onClose() {
        BigDecimal cash = DecimalInput.parseOrNull(cntCash.getText());
        BigDecimal pix = DecimalInput.parseOrNull(cntPix.getText());
        BigDecimal credit = DecimalInput.parseOrNull(cntCredit.getText());
        BigDecimal debit = DecimalInput.parseOrNull(cntDebit.getText());
        if (cash == null || pix == null || credit == null || debit == null) {
            onFinished.accept("Informe o valor contado de Dinheiro, PIX, Crédito e Débito (use 0 se não houve).");
            return;
        }
        onFinished.accept(flow.close(userSession.getLoggedUser(), new ClosingCount(cash, pix, credit, debit)));
    }

    private void fillCounted(CashRegister register, boolean open) {
        if (open || register == null || register.getExpectedPix() == null) {
            cntCash.clear();
            cntPix.clear();
            cntCredit.clear();
            cntDebit.clear();
            if (!open && register != null && register.getClosingAmount() != null) {
                cntCash.setText(plain(register.getClosingAmount()));
            }
            return;
        }
        cntCash.setText(plain(register.getClosingAmount()));
        cntPix.setText(plain(register.getCountedPix()));
        cntCredit.setText(plain(register.getCountedCredit()));
        cntDebit.setText(plain(register.getCountedDebit()));
    }

    private void fillExpected(CashRegister register, CashSummary summary, boolean open) {
        boolean stored = !open && register != null && register.getExpectedPix() != null;
        expCash.setText(MoneyFormat.format(stored ? register.getExpectedAmount() : summary.finalBalance()));
        expPix.setText(MoneyFormat.format(stored ? register.getExpectedPix() : summary.pix()));
        expCredit.setText(MoneyFormat.format(stored ? register.getExpectedCredit() : summary.credit()));
        expDebit.setText(MoneyFormat.format(stored ? register.getExpectedDebit() : summary.debit()));
    }

    private void refreshDifferences() {
        BigDecimal total = BigDecimal.ZERO;
        boolean complete = true;
        boolean balanced = true;
        TextField[] counted = {cntCash, cntPix, cntCredit, cntDebit};
        TextField[] expected = {expCash, expPix, expCredit, expDebit};
        Label[] labels = {difCash, difPix, difCredit, difDebit};
        for (int i = 0; i < counted.length; i++) {
            BigDecimal value = DecimalInput.parseOrNull(counted[i].getText());
            BigDecimal expectedValue = parseMoney(expected[i].getText());
            if (value == null) {
                complete = false;
                labels[i].setText("");
                continue;
            }
            BigDecimal difference = value.subtract(expectedValue);
            total = total.add(difference);
            balanced &= difference.signum() == 0;
            labels[i].setText("Diferença: " + MoneyFormat.format(difference));
            style(labels[i], difference.signum() != 0);
        }
        showTotal(total, complete, balanced);
    }

    private void showTotal(BigDecimal total, boolean complete, boolean balanced) {
        if (!complete) {
            totalDifferenceLabel.setText(editable ? "Informe o contado de cada forma de pagamento." : "");
            totalDifferenceLabel.getStyleClass().removeAll(BAD, OK);
            return;
        }
        if (balanced) {
            totalDifferenceLabel.setText("Caixa confere: sem diferença.");
        } else {
            totalDifferenceLabel.setText((total.signum() < 0 ? "FALTA " : total.signum() > 0 ? "SOBRA " : "DIVERGÊNCIA ")
                    + MoneyFormat.format(total.abs()) + " — diferença total: " + MoneyFormat.format(total));
        }
        style(totalDifferenceLabel, !balanced);
    }

    private void style(Label label, boolean bad) {
        label.getStyleClass().removeAll(BAD, OK);
        label.getStyleClass().add(bad ? BAD : OK);
    }

    private BigDecimal parseMoney(String text) {
        String digits = text == null ? "" : text.replace("R$", "").replace(".", "").trim();
        return DecimalInput.parseOrZero(digits);
    }

    private String plain(BigDecimal value) {
        return value == null ? "" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
