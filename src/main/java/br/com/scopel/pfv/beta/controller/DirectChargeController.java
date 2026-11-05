package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.AmountInput;
import br.com.scopel.pfv.beta.controller.support.CardChargePoller;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DirectChargeController {

    private enum State { INPUT, CHOOSE, CHARGING }

    private static final String BACKSPACE_KEY = "⌫";

    private final CardPaymentGateway gateway;
    private final DirectChargeService directChargeService;
    private final UserSession userSession;
    private final AmountInput input = new AmountInput();

    private State state = State.INPUT;
    private BigDecimal amount;
    private PaymentMethod method;
    private CardType cardType;
    private CardChargePoller poller;

    @FXML private BorderPane root;
    @FXML private Label displayLabel;
    @FXML private Node inputPanel;
    @FXML private Node choosePanel;
    @FXML private Node chargingPanel;
    @FXML private Label chooseAmountLabel;
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
        show(State.INPUT);
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null && poller != null) {
                poller.stop();
            }
        });
        Platform.runLater(root::requestFocus);
    }

    @FXML
    public void onKeypad(ActionEvent event) {
        pressKey((String) ((Node) event.getSource()).getUserData());
    }

    @FXML
    public void onEnter() {
        if (state != State.INPUT) {
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
        show(State.CHOOSE);
    }

    @FXML
    public void onDebit() {
        startCharge(PaymentMethod.CARTAO, CardType.DEBITO, "Débito");
    }

    @FXML
    public void onCredit() {
        startCharge(PaymentMethod.CARTAO, CardType.CREDITO, "Crédito");
    }

    @FXML
    public void onPix() {
        startCharge(PaymentMethod.PIX, null, "PIX");
    }

    @FXML
    public void onBack() {
        if (state == State.CHOOSE) {
            show(State.INPUT);
        }
    }

    @FXML
    public void onCancelCharge() {
        if (state != State.CHARGING || poller == null) {
            return;
        }
        statusLabel.setText("Cancelando a cobrança na maquininha...");
        poller.cancel(() -> {
            poller = null;
            show(State.CHOOSE);
        }, statusLabel::setText);
    }

    private void onKey(KeyEvent event) {
        boolean handled = true;
        if (state == State.INPUT) {
            handled = handleInputKey(event);
        } else if (state == State.CHOOSE) {
            switch (event.getCode()) {
                case DIGIT1, NUMPAD1 -> onDebit();
                case DIGIT2, NUMPAD2 -> onCredit();
                case DIGIT3, NUMPAD3 -> onPix();
                case ESCAPE -> onBack();
                default -> handled = false;
            }
        } else if (event.getCode() == KeyCode.ESCAPE) {
            onCancelCharge();
        } else {
            handled = false;
        }
        if (handled) {
            event.consume();
        }
    }

    private boolean handleInputKey(KeyEvent event) {
        String text = event.getText();
        KeyCode code = event.getCode();
        if (code == KeyCode.ENTER) {
            onEnter();
        } else if (code == KeyCode.BACK_SPACE) {
            pressKey(BACKSPACE_KEY);
        } else if (code == KeyCode.ESCAPE) {
            pressKey("C");
        } else if (code == KeyCode.DECIMAL || ",".equals(text) || ".".equals(text)) {
            pressKey(",");
        } else if (text != null && text.length() == 1 && Character.isDigit(text.charAt(0))) {
            pressKey(text);
        } else {
            return false;
        }
        return true;
    }

    private void pressKey(String key) {
        messageLabel.setText("");
        switch (key) {
            case BACKSPACE_KEY -> input.backspace();
            case "C" -> input.clear();
            case "," -> input.comma();
            default -> input.digit(key.charAt(0));
        }
        displayLabel.setText("R$ " + input.display());
    }

    private void startCharge(PaymentMethod chosenMethod, CardType chosenCardType, String label) {
        if (state != State.CHOOSE) {
            return;
        }
        method = chosenMethod;
        cardType = chosenCardType;
        BigDecimal value = amount;
        chargingTitleLabel.setText("Cobrança no " + label);
        chargingAmountLabel.setText(MoneyFormat.format(value));
        statusLabel.setText("Enviando a cobrança para a maquininha...");
        show(State.CHARGING);
        poller = new CardChargePoller(gateway, statusLabel::setText, () -> approved(label), this::ended);
        poller.start(() -> chosenMethod == PaymentMethod.PIX
                ? gateway.createPixCharge(value)
                : gateway.createCharge(value, chosenCardType));
    }

    private void approved(String label) {
        poller = null;
        String registration = "";
        try {
            directChargeService.register(amount, method, cardType, userSession.getLoggedUser(),
                    userSession.getCaixaAtual());
        } catch (RuntimeException failure) {
            registration = " ATENÇÃO: não foi possível registrar a cobrança (" + failure.getMessage() + ").";
        }
        String approvedMessage = "✔ Cobrança aprovada: " + MoneyFormat.format(amount) + " (" + label + ")";
        input.clear();
        displayLabel.setText("R$ " + input.display());
        show(State.INPUT);
        messageLabel.setText(approvedMessage + registration);
    }

    private void ended(String message) {
        poller = null;
        show(State.CHOOSE);
        messageLabel.setText(message);
    }

    private void show(State newState) {
        state = newState;
        setVisible(inputPanel, newState == State.INPUT);
        setVisible(choosePanel, newState == State.CHOOSE);
        setVisible(chargingPanel, newState == State.CHARGING);
        if (newState != State.INPUT) {
            messageLabel.setText("");
        }
        Platform.runLater(root::requestFocus);
    }

    private void setVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
