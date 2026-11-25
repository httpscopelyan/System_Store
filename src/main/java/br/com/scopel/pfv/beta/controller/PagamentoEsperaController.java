package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.ChargeCancellation;
import br.com.scopel.pfv.beta.controller.support.CardChargePoller;
import br.com.scopel.pfv.beta.controller.support.ChargeScreen;
import br.com.scopel.pfv.beta.controller.support.ManualCardApproval;
import br.com.scopel.pfv.beta.controller.support.ManualCardEntry;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PagamentoEsperaController {

    private final SaleService saleService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final ReceiptService receiptService;
    private final CardPaymentGateway gateway;
    private Sale vendaAtual;
    private CardType cardType;
    private boolean done, ended, awaitingRegistration, manuallyConfirmed;
    private CardChargePoller poller;
    private ChargeCancellation cancellation = new ChargeCancellation();
    private ChargeScreen screen;
    private final ManualCardApproval manualApproval = new ManualCardApproval();

    @FXML private Label totalLabel;
    @FXML private Label typeLabel;
    @FXML private Label statusLabel;
    @FXML private Button confirmButton;
    @FXML private Button manualConfirmButton;
    @FXML private VBox manualBox;
    @FXML private TextField receivedField;

    public PagamentoEsperaController(SaleService saleService, UserSession userSession, Navigator navigator,
                                     ReceiptService receiptService, CardPaymentGateway gateway) {
        this.saleService = saleService;
        this.userSession = userSession;
        this.navigator = navigator;
        this.receiptService = receiptService;
        this.gateway = gateway;
    }

    @FXML
    public void initialize() {
        navigator.setPageBackHandler(this::onCancel);
        screen = new ChargeScreen(statusLabel, confirmButton);
        vendaAtual = userSession.getVendaAtual();
        cardType = userSession.getCardTypeSelecionado();
        done = ended = awaitingRegistration = manuallyConfirmed = false;
        poller = null;
        BigDecimal total = vendaAtual == null ? BigDecimal.ZERO : vendaAtual.getTotal();
        totalLabel.setText("R$ " + ManualCardEntry.money(total));
        typeLabel.setText(cardType == null ? "-" : cardType.name());
        if (vendaAtual == null || cardType == null) {
            statusLabel.setText("Dados da cobrança ausentes.");
            return;
        }
        if (gateway.isIntegrated()) {
            startIntegratedCharge(total);
        } else {
            startManualCharge(total);
        }
    }

    @FXML
    public void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) {
            onConfirm();
            e.consume();
        } else if (e.getCode() == KeyCode.ESCAPE) {
            onCancel();
            e.consume();
        }
    }

    @FXML
    public void onConfirm() {
        if (ended) {
            leaveTo(Page.PAGAMENTO);
            return;
        }
        if (awaitingRegistration) {
            approve();
            return;
        }
        if (done || gateway.isIntegrated() || vendaAtual == null || cardType == null) {
            return;
        }
        String problem = ManualCardEntry.problemWith(receivedField.getText(), vendaAtual.getTotal());
        if (problem != null) {
            statusLabel.setText(problem);
            return;
        }
        approve();
    }

    @FXML
    public void onManualConfirm() {
        if ((done && !ended) || awaitingRegistration) {
            return;
        }
        manualApproval.request(ManualCardEntry.money(vendaAtual.getTotal()), cardType, ended ? null : poller,
                statusLabel::setText, () -> {
                    manuallyConfirmed = true;
                    done = ended = false;
                    approve();
                });
    }

    @FXML
    public void onCancel() {
        if (ended) {
            leaveTo(Page.PAGAMENTO);
            return;
        }
        if (done || awaitingRegistration) {
            return;
        }
        cancellation.request(poller, () -> done, statusLabel::setText, () -> {
            done = true;
            leaveTo(Page.PAGAMENTO);
        });
    }

    private void leaveTo(Page page) {
        navigator.unlock();
        navigator.navigate(page);
    }

    private void startManualCharge(BigDecimal total) {
        ManualCardEntry.show(manualBox, receivedField, statusLabel, total);
    }

    private void startIntegratedCharge(BigDecimal total) {
        screen.hideConfirm();
        manualConfirmButton.setVisible(true);
        manualConfirmButton.setManaged(true);
        navigator.lock("Cobrança em andamento na maquininha. Use ESC para cancelar antes de sair desta tela.");
        statusLabel.setText("Enviando a cobrança para a maquininha...");
        poller = new CardChargePoller(gateway, statusLabel::setText, this::approve, this::backToSelection);
        statusLabel.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null && poller != null) {
                poller.stop();
                navigator.unlock();
            }
        });
        poller.start(() -> gateway.createCharge(total, cardType));
    }

    private void approve() {
        if (done) {
            return;
        }
        done = true;
        cancellation.dismiss();
        Sale finalizada;
        try {
            finalizada = manuallyConfirmed
                    ? saleService.payCardConfirmedManually(vendaAtual, cardType)
                    : saleService.payCard(vendaAtual, cardType, poller == null ? null : poller.chargeId());
        } catch (RuntimeException failure) {
            done = false;
            awaitingRegistration = true;
            screen.showRegistrationFailure(failure.getMessage());
            return;
        }
        userSession.setUltimaVenda(finalizada);
        userSession.setUltimaForma(PaymentMethod.CARTAO);
        userSession.setUltimoTroco(null);
        userSession.setVendaAtual(null);
        leaveTo(Page.VENDA_FINALIZADA);
    }

    private void backToSelection(String message) {
        done = true;
        ended = true;
        navigator.unlock();
        screen.showEnded(message);
    }
}
