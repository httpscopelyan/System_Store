package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.AmountInput;
import br.com.scopel.pfv.beta.controller.support.CardChargePoller;
import br.com.scopel.pfv.beta.controller.support.DirectChargeKeyboard;
import br.com.scopel.pfv.beta.controller.support.DirectChargePanels;
import br.com.scopel.pfv.beta.controller.support.DirectChargeRequest;
import br.com.scopel.pfv.beta.controller.support.DirectChargePanels.State;
import br.com.scopel.pfv.beta.controller.support.MaxLengthInput;
import br.com.scopel.pfv.beta.controller.support.MoneyFormat;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.service.DirectChargeService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DirectChargeController implements DirectChargeKeyboard.Actions {

    private final CardPaymentGateway gateway;
    private final DirectChargeService directChargeService;
    private final UserSession userSession;
    private final AmountInput input = new AmountInput();
    private final DirectChargeKeyboard keyboard = new DirectChargeKeyboard(this);

    private DirectChargePanels panels;
    private BigDecimal amount;
    private CardChargePoller poller;

    @FXML private BorderPane root;
    @FXML private Label displayLabel;
    @FXML private TextField noteField;
    @FXML private Node inputPanel;
    @FXML private Node choosePanel;
    @FXML private Node chargingPanel;
    @FXML private Label chooseAmountLabel;
    @FXML private Label chooseNoteLabel;
    @FXML private Label chargingAmountLabel;
    @FXML private Label chargingTitleLabel;
    @FXML private Label statusLabel;
    @FXML private Label messageLabel;

    public DirectChargeController(CardPaymentGateway gateway, DirectChargeService directChargeService,
                                  UserSession userSession) {
        this.gateway = gateway;
        this.directChargeService = directChargeService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        input.clear();
        amount = null;
        poller = null;
        panels = new DirectChargePanels(inputPanel, choosePanel, chargingPanel);
        MaxLengthInput.limit(noteField, DirectChargeService.MAX_NOTE_LENGTH);
        show(State.INPUT);
        root.addEventFilter(KeyEvent.KEY_PRESSED,
                event -> keyboard.handle(event, panels.state(), noteField.isFocused()));
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null && poller != null) {
                poller.stop();
            }
        });
    }

    @FXML
    public void onKeypad(ActionEvent event) {
        pressKeypad((String) ((Node) event.getSource()).getUserData());
    }

    @FXML
    @Override
    public void onEnter() {
        if (!panels.is(State.INPUT)) {
            return;
        }
        if (!input.isPositive()) {
            messageLabel.setText("Digite o valor a cobrar.");
            return;
        }
        if (!gateway.isIntegrated()) {
            messageLabel.setText("A maquininha não está no modo PDV. Mude o modo em Configurações → Maquininha.");
            return;
        }
        amount = input.value();
        chooseAmountLabel.setText(MoneyFormat.format(amount));
        chooseNoteLabel.setText(note().isEmpty() ? "" : "Observação: " + note());
        show(State.CHOOSE);
    }

    @FXML
    @Override
    public void onDebit() {
        startCharge(PaymentMethod.CARTAO, CardType.DEBITO);
    }

    @FXML
    @Override
    public void onCredit() {
        startCharge(PaymentMethod.CARTAO, CardType.CREDITO);
    }

    @FXML
    @Override
    public void onPix() {
        startCharge(PaymentMethod.PIX, null);
    }

    @FXML
    @Override
    public void onBack() {
        if (panels.is(State.CHOOSE)) {
            show(State.INPUT);
        }
    }

    @FXML
    @Override
    public void onCancelCharge() {
        if (!panels.is(State.CHARGING) || poller == null) {
            return;
        }
        statusLabel.setText("Cancelando a cobrança na maquininha...");
        poller.cancel(() -> {
            poller = null;
            show(State.CHOOSE);
        }, statusLabel::setText);
    }

    @Override
    public void onLeaveNote() {
        root.requestFocus();
    }

    @Override
    public void pressKeypad(String key) {
        messageLabel.setText("");
        switch (key) {
            case DirectChargeKeyboard.BACKSPACE_KEY -> input.backspace();
            case DirectChargeKeyboard.CLEAR_KEY -> input.clear();
            case DirectChargeKeyboard.COMMA_KEY -> input.comma();
            default -> input.digit(key.charAt(0));
        }
        displayLabel.setText("R$ " + input.display());
    }

    private String note() {
        return noteField.getText() == null ? "" : noteField.getText().trim();
    }

    private void startCharge(PaymentMethod chosenMethod, CardType chosenCardType) {
        if (!panels.is(State.CHOOSE)) {
            return;
        }
        DirectChargeRequest sentRequest = new DirectChargeRequest(amount, chosenMethod, chosenCardType, note());
        chargingTitleLabel.setText("Cobrança no " + sentRequest.methodLabel());
        chargingAmountLabel.setText(MoneyFormat.format(sentRequest.amount()));
        statusLabel.setText("Enviando a cobrança para a maquininha...");
        show(State.CHARGING);
        poller = new CardChargePoller(gateway, statusLabel::setText, () -> approved(sentRequest), this::ended);
        poller.start(() -> sentRequest.createOn(gateway));
    }

    private void approved(DirectChargeRequest approvedRequest) {
        poller = null;
        String outcome = approvedRequest.registerApproved(directChargeService, userSession.getLoggedUser(),
                userSession.getCaixaAtual());
        input.clear();
        noteField.clear();
        displayLabel.setText("R$ " + input.display());
        show(State.INPUT);
        messageLabel.setText(outcome);
    }

    private void ended(String message) {
        poller = null;
        show(State.CHOOSE);
        messageLabel.setText(message);
    }

    private void show(State newState) {
        panels.show(newState);
        if (newState != State.INPUT) {
            messageLabel.setText("");
        }
        Platform.runLater(root::requestFocus);
    }
}
