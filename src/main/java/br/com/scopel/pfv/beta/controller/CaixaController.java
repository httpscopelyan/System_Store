package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.CashClosingFlow;
import br.com.scopel.pfv.beta.controller.support.CashHistoryPresenter;
import br.com.scopel.pfv.beta.controller.support.CashMovementActions;
import br.com.scopel.pfv.beta.controller.support.CashStatusPanel;
import br.com.scopel.pfv.beta.controller.support.DecimalInput;
import br.com.scopel.pfv.beta.controller.support.ManagerAuthorizedAction;
import br.com.scopel.pfv.beta.controller.support.ScenePoller;
import br.com.scopel.pfv.beta.exception.CashRegisterAlreadyOpenException;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.cash.CashMovementRow;
import br.com.scopel.pfv.beta.service.cash.CashOverview;
import br.com.scopel.pfv.beta.service.cash.CashSummary;
import br.com.scopel.pfv.beta.service.cash.CashSummaryService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CaixaController {

    private static final String OTHER_REASON = "Outro";
    private static final List<String> REASONS = List.of("Troco inicial", "Depósito bancário",
            "Pagamento a fornecedor", "Retirada do proprietário", OTHER_REASON);

    private final CashRegisterService cashRegisterService;
    private final CashSummaryService cashSummaryService;
    private final UserSession userSession;
    private final ReceiptService receiptService;
    private final ManagerAuthorizationController managerAuthorizationController;

    private CashRegister displayed;
    private ScenePoller poller;
    private CashHistoryPresenter history;
    private CashStatusPanel statusPanel;
    private CashClosingFlow closingFlow;
    private CashMovementActions movementActions;

    @FXML private ComboBox<String> terminalBox;
    @FXML private Label statusBigLabel;
    @FXML private TableView<CashRegister> historyTable;
    @FXML private TableColumn<CashRegister, String> colHistDate;
    @FXML private TableColumn<CashRegister, String> colHistBalance;
    @FXML private Label openingInfoLabel;
    @FXML private Label closingInfoLabel;
    @FXML private CashMovementsController cashMovementsController;
    @FXML private CashSummaryController cashSummaryController;
    @FXML private TextField openingAmountField;
    @FXML private Label periodLabel;
    @FXML private TextField movementAmountField;
    @FXML private ComboBox<String> movementReasonBox;
    @FXML private TextField countedAmountField;
    @FXML private Label statusLabel;

    public CaixaController(CashRegisterService cashRegisterService, CashSummaryService cashSummaryService,
                           UserSession userSession, ReceiptService receiptService,
                           ManagerAuthorizationController managerAuthorizationController) {
        this.cashRegisterService = cashRegisterService;
        this.cashSummaryService = cashSummaryService;
        this.userSession = userSession;
        this.receiptService = receiptService;
        this.managerAuthorizationController = managerAuthorizationController;
    }

    @FXML
    public void initialize() {
        terminalBox.setItems(FXCollections.observableArrayList("CAIXA 01"));
        terminalBox.setValue("CAIXA 01");
        history = new CashHistoryPresenter(historyTable, colHistDate, colHistBalance);
        statusPanel = new CashStatusPanel(statusBigLabel, openingInfoLabel, closingInfoLabel, periodLabel);
        closingFlow = new CashClosingFlow(cashRegisterService, receiptService, userSession);
        movementActions = new CashMovementActions(cashRegisterService,
                new ManagerAuthorizedAction(managerAuthorizationController, userSession, this::window), this::window);
        movementReasonBox.setItems(FXCollections.observableArrayList(REASONS));
        movementReasonBox.valueProperty().addListener((observable, previous, current) -> {
            if (OTHER_REASON.equals(current)) {
                movementReasonBox.getEditor().clear();
                movementReasonBox.getEditor().requestFocus();
            }
        });
        onRefresh();
        if (poller != null) {
            poller.stop();
        }
        poller = ScenePoller.start(statusLabel, Duration.seconds(4), this::onRefresh);
    }

    @FXML
    public void onRefresh() {
        User operator = userSession.getLoggedUser();
        List<CashRegister> registers = operator == null ? List.of() : cashRegisterService.history(operator);
        history.show(registers);
        CashRegister current = userSession.getCaixaAtual();
        displayed = current != null ? current : (registers.isEmpty() ? null : registers.get(0));
        if (displayed == null) {
            statusPanel.showEmpty();
            cashMovementsController.show(List.of());
            cashSummaryController.show(CashSummary.empty());
            return;
        }
        statusPanel.show(displayed);
        CashOverview overview = cashSummaryService.overview(displayed);
        cashMovementsController.show(overview.rows());
        cashSummaryController.show(overview.summary());
    }

    @FXML
    public void onOpen() {
        try {
            BigDecimal openingAmount = DecimalInput.parseRequired(openingAmountField.getText());
            userSession.setCaixaAtual(cashRegisterService.open(userSession.getLoggedUser(), openingAmount));
            openingAmountField.clear();
            statusLabel.setText("Caixa aberto!");
            onRefresh();
        } catch (NumberFormatException invalidNumber) {
            statusLabel.setText("Valor inválido");
        } catch (CashRegisterAlreadyOpenException alreadyOpen) {
            statusLabel.setText("Já existe um caixa aberto");
        }
    }

    @FXML
    public void onSupplement() {
        registerMovement(true);
    }

    @FXML
    public void onWithdrawal() {
        registerMovement(false);
    }

    @FXML
    public void onRectify() {
        CashMovementActions.Result result = movementActions.rectify(cashMovementsController.selected());
        if (!result.message().isEmpty()) {
            statusLabel.setText(result.message());
        }
        if (result.success()) {
            onRefresh();
        }
    }

    @FXML
    public void onClose() {
        String message = closingFlow.close(openRegister(), countedAmountField.getText());
        if (message.startsWith("Caixa FECHADO")) {
            countedAmountField.clear();
        }
        statusLabel.setText(message);
        onRefresh();
    }

    @FXML
    public void onPrintMovimentacao() {
        statusLabel.setText("Impressão da movimentação: use a impressora configurada (em ajuste).");
    }

    @FXML
    public void onPrintFechamento() {
        statusLabel.setText(closingFlow.printClosing(displayed));
    }

    private void registerMovement(boolean supplement) {
        CashMovementActions.Result result = movementActions.register(openRegister(), supplement,
                movementAmountField.getText(), movementReasonBox.getEditor().getText());
        statusLabel.setText(result.message());
        if (result.success()) {
            movementAmountField.clear();
            movementReasonBox.setValue(null);
            movementReasonBox.getEditor().clear();
            onRefresh();
        }
    }

    private CashRegister openRegister() {
        User operator = userSession.getLoggedUser();
        return operator == null ? null : cashRegisterService.findOpenByOperator(operator).orElse(null);
    }

    private Window window() {
        return statusLabel.getScene() == null ? null : statusLabel.getScene().getWindow();
    }
}
