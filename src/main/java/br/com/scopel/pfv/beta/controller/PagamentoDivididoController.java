package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.MoneyFormat;
import br.com.scopel.pfv.beta.controller.support.SplitAmountDecision;
import br.com.scopel.pfv.beta.controller.support.SplitCashParts;
import br.com.scopel.pfv.beta.controller.support.SplitChargeSession;
import br.com.scopel.pfv.beta.controller.support.SplitManualCharge;
import br.com.scopel.pfv.beta.controller.support.SplitPartMethod;
import br.com.scopel.pfv.beta.controller.support.SplitPartRegistrar;
import br.com.scopel.pfv.beta.controller.support.SplitPaymentKeys;
import br.com.scopel.pfv.beta.controller.support.SplitPaymentScreen;
import br.com.scopel.pfv.beta.controller.support.SplitPaymentView;
import br.com.scopel.pfv.beta.controller.support.SplitStep;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.PixService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PagamentoDivididoController {

    private final SplitPaymentService splitPaymentService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final CardPaymentGateway gateway;
    private final PixService pixService;
    private final ReceiptService receiptService;

    private Sale saleInPayment;
    private SplitStep step = SplitStep.CHOOSING_METHOD;
    private SplitPartMethod chosenMethod;
    private BigDecimal pendingAmount;
    private SplitPaymentView view;
    private SplitPaymentScreen screen;
    private SplitManualCharge manualCharge;
    private SplitPartRegistrar registrar;
    private SplitChargeSession charge;
    private SplitCashParts cashParts;

    @FXML private BorderPane root;
    @FXML private Node methodPanel;
    @FXML private Node amountPanel;
    @FXML private Label partMethodLabel;
    @FXML private TextField partAmountField;
    @FXML private Node manualBox;
    @FXML private TextField machineReceivedField;
    @FXML private Node pixBox;
    @FXML private TextArea pixArea;
    @FXML private Label statusLabel;
    @FXML private Label failureLabel;
    @FXML private Button confirmButton;
    @FXML private Label totalLabel;
    @FXML private Label paidLabel;
    @FXML private Label remainingLabel;
    @FXML private VBox partsBox;

    public PagamentoDivididoController(SplitPaymentService splitPaymentService, UserSession userSession,
                                       Navigator navigator, ReceiptService receiptService,
                                       CardPaymentGateway gateway, PixService pixService) {
        this.splitPaymentService = splitPaymentService;
        this.userSession = userSession;
        this.navigator = navigator;
        this.receiptService = receiptService;
        this.gateway = gateway;
        this.pixService = pixService;
    }

    @FXML
    public void initialize() {
        navigator.setPageBackHandler(this::onBack);
        saleInPayment = userSession.getVendaAtual();
        view = new SplitPaymentView(totalLabel, paidLabel, remainingLabel, partsBox, this::removeCashPart);
        cashParts = new SplitCashParts(splitPaymentService, view);
        screen = new SplitPaymentScreen(methodPanel, amountPanel, manualBox, pixBox, confirmButton, failureLabel,
                partAmountField, root);
        manualCharge = new SplitManualCharge(pixService, machineReceivedField, pixArea);
        registrar = new SplitPartRegistrar(splitPaymentService, receiptService, userSession, navigator, view, screen);
        charge = new SplitChargeSession(gateway, navigator, statusLabel::setText);
        new SplitPaymentKeys(() -> step, this::onBack, this::onConfirm,
                () -> view.lastCashPart().ifPresent(this::removeCashPart), this::chooseMethod).install(root);
        charge.abandonWhenLeft(root);
        if (saleInPayment == null) {
            statusLabel.setText("Nenhuma venda em pagamento.");
            return;
        }
        view.show(splitPaymentService.status(saleInPayment));
        statusLabel.setText(view.hasElectronicPart()
                ? "Venda com pagamento já recebido. Conclua o valor restante." : "");
        showStep(SplitStep.CHOOSING_METHOD);
    }

    @FXML public void onCash() { chooseMethod(SplitPartMethod.CASH); }
    @FXML public void onDebit() { chooseMethod(SplitPartMethod.DEBIT); }
    @FXML public void onCredit() { chooseMethod(SplitPartMethod.CREDIT); }
    @FXML public void onPix() { chooseMethod(SplitPartMethod.PIX); }

    @FXML
    public void onConfirm() {
        if (step == SplitStep.REGISTRATION_FAILED) {
            registerPart(chosenMethod, pendingAmount);
        } else if (step == SplitStep.ENTERING_AMOUNT) {
            confirmAmount();
        }
    }

    @FXML
    public void onBack() {
        switch (step) {
            case ENTERING_AMOUNT -> {
                statusLabel.setText("");
                showStep(SplitStep.CHOOSING_METHOD);
            }
            case CHARGING -> charge.requestCancel(() -> step != SplitStep.CHARGING,
                    () -> chargeStopped("Cobrança cancelada."));
            case CHOOSING_METHOD -> cashParts.discardAllOrExplainRefusal(saleInPayment)
                    .ifPresentOrElse(statusLabel::setText, () -> navigator.navigate(Page.PAGAMENTO));
            default -> { }
        }
    }

    private void chooseMethod(SplitPartMethod method) {
        if (step != SplitStep.CHOOSING_METHOD || saleInPayment == null) {
            return;
        }
        chosenMethod = method;
        manualCharge.reset();
        partMethodLabel.setText("Valor desta parte em " + method.label());
        partAmountField.setText(MoneyFormat.plain(view.remainingAmount()));
        statusLabel.setText("");
        showStep(SplitStep.ENTERING_AMOUNT);
    }

    private void confirmAmount() {
        SplitAmountDecision decision = SplitAmountDecision.of(partAmountField.getText(), chosenMethod,
                view.remainingAmount(), gateway.isIntegrated());
        BigDecimal amount = decision.amount();
        switch (decision.action()) {
            case REJECT -> statusLabel.setText(decision.message());
            case REGISTER_NOW -> registerPart(chosenMethod, amount);
            case START_MACHINE_CHARGE -> {
                pendingAmount = amount;
                showStep(SplitStep.CHARGING);
                charge.start(chosenMethod, amount, () -> registerPart(chosenMethod, amount), this::chargeStopped);
            }
            case CONFIRM_MANUALLY -> confirmManually(amount);
        }
    }

    private void confirmManually(BigDecimal amount) {
        SplitManualCharge.Outcome outcome = manualCharge.confirm(chosenMethod, amount, saleInPayment.getId());
        screen.showPix(manualCharge.isPixShown());
        if (outcome.readyToRegister()) {
            registerPart(chosenMethod, amount);
        } else {
            statusLabel.setText(outcome.message());
        }
    }

    private void chargeStopped(String message) {
        charge.release();
        statusLabel.setText(message + " Nenhum valor foi registrado.");
        showStep(SplitStep.ENTERING_AMOUNT);
    }

    private void registerPart(SplitPartMethod method, BigDecimal amount) {
        pendingAmount = amount;
        charge.release();
        SplitPartRegistrar.Registration registration = registrar.register(saleInPayment, method, amount);
        saleInPayment = registration.sale();
        if (registration.nextStep() != null) {
            statusLabel.setText(registration.message());
            showStep(registration.nextStep());
        }
    }

    private void removeCashPart(Payment cashPart) {
        statusLabel.setText(cashParts.remove(saleInPayment, cashPart));
    }

    private void showStep(SplitStep newStep) {
        step = newStep;
        screen.show(newStep, chosenMethod, gateway.isIntegrated(), manualCharge.isPixShown());
    }
}
