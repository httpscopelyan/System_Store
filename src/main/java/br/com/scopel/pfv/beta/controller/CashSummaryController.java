package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.CashClosingFlow;
import br.com.scopel.pfv.beta.controller.support.CashClosingLine;
import br.com.scopel.pfv.beta.controller.support.CashClosingPanel;
import br.com.scopel.pfv.beta.controller.support.MoneyFormat;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.service.cash.CashSummary;
import br.com.scopel.pfv.beta.service.cash.ClosingCount;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Consumer;

@Component
public class CashSummaryController {

    private final UserSession userSession;

    private CashClosingFlow closingFlow;
    private CashClosingPanel closingPanel;
    private Consumer<String> onClosingFinished = message -> { };

    @FXML private TextField openingBalanceField;
    @FXML private TextField expectedCashInField;
    @FXML private TextField expectedPixField;
    @FXML private TextField expectedCreditField;
    @FXML private TextField expectedDebitField;
    @FXML private TextField expectedExtraField;
    @FXML private TextField totalInField;
    @FXML private TextField cashOutField;
    @FXML private TextField totalOutField;
    @FXML private TextField expectedDrawerBalanceField;
    @FXML private TextField countedPixField;
    @FXML private TextField countedCreditField;
    @FXML private TextField countedDebitField;
    @FXML private TextField countedExtraField;
    @FXML private TextField countedDrawerField;
    @FXML private Label pixDifferenceLabel;
    @FXML private Label creditDifferenceLabel;
    @FXML private Label debitDifferenceLabel;
    @FXML private Label extraDifferenceLabel;
    @FXML private Label drawerDifferenceLabel;
    @FXML private Label extraNoteLabel;
    @FXML private TextField extraNoteField;
    @FXML private Label totalDifferenceLabel;
    @FXML private Button closeButton;

    public CashSummaryController(UserSession userSession) {
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        closingPanel = new CashClosingPanel(
                new CashClosingLine(expectedDrawerBalanceField, countedDrawerField, drawerDifferenceLabel),
                new CashClosingLine(expectedPixField, countedPixField, pixDifferenceLabel),
                new CashClosingLine(expectedCreditField, countedCreditField, creditDifferenceLabel),
                new CashClosingLine(expectedDebitField, countedDebitField, debitDifferenceLabel),
                new CashClosingLine(expectedExtraField, countedExtraField, extraDifferenceLabel),
                extraNoteLabel, extraNoteField, totalDifferenceLabel, closeButton);
    }

    public void bind(CashClosingFlow flow, Consumer<String> closingFinished) {
        this.closingFlow = flow;
        this.onClosingFinished = closingFinished;
    }

    public void show(CashRegister register, CashSummary summary) {
        openingBalanceField.setText(MoneyFormat.format(summary.opening()));
        expectedCashInField.setText(MoneyFormat.format(summary.cashIn()));
        totalInField.setText(MoneyFormat.format(summary.totalIn()));
        cashOutField.setText(MoneyFormat.format(summary.cashOut()));
        totalOutField.setText(MoneyFormat.format(summary.cashOut()));
        closingPanel.show(register, summary);
    }

    @FXML
    public void onClose() {
        Optional<ClosingCount> closingCount = closingPanel.closingCount();
        if (closingCount.isEmpty()) {
            onClosingFinished.accept(CashClosingPanel.MISSING_COUNT_MESSAGE);
            return;
        }
        if (closingPanel.isExtraNoteMissing()) {
            onClosingFinished.accept(CashClosingPanel.MISSING_EXTRA_NOTE_MESSAGE);
            closingPanel.focusExtraNote();
            return;
        }
        onClosingFinished.accept(closingFlow.close(userSession.getLoggedUser(), closingCount.get()));
    }
}
