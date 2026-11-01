package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.CardChargePoller;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@Component
public class PagamentoEsperaController {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    private final SaleService saleService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final ReceiptService receiptService;
    private final CardPaymentGateway gateway;

    private Sale vendaAtual;
    private CardType cardType;
    private boolean done;
    private CardChargePoller poller;

    @FXML private Label totalLabel;
    @FXML private Label typeLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label statusLabel;
    @FXML private Button confirmButton;

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
        vendaAtual = userSession.getVendaAtual();
        cardType = userSession.getCardTypeSelecionado();
        done = false;
        poller = null;

        BigDecimal total = vendaAtual == null ? BigDecimal.ZERO : vendaAtual.getTotal();
        totalLabel.setText("R$ " + MONEY.format(total));
        typeLabel.setText(cardType == null ? "-" : cardType.name());

        if (vendaAtual == null || cardType == null) {
            statusLabel.setText("Dados da cobrança ausentes.");
            return;
        }
        if (gateway.isIntegrated()) {
            startIntegratedCharge(total);
        } else {
            statusLabel.setText("Passe o cartão na maquininha. Aprovou? Pressione ENTER para confirmar.");
        }
    }

    @FXML
    public void onKey(KeyEvent e) {
        switch (e.getCode()) {
            case ENTER -> {
                onConfirm();
                e.consume();
            }
            case ESCAPE -> {
                onCancel();
                e.consume();
            }
            default -> {
            }
        }
    }

    @FXML
    public void onConfirm() {
        if (done || gateway.isIntegrated() || vendaAtual == null || cardType == null) {
            return;
        }
        approve();
    }

    @FXML
    public void onCancel() {
        if (done) {
            return;
        }
        if (poller == null) {
            done = true;
            navigator.navigate(Page.PAGAMENTO);
            return;
        }
        statusLabel.setText("Cancelando a cobrança na maquininha...");
        poller.cancel(() -> {
            done = true;
            navigator.navigate(Page.PAGAMENTO);
        }, statusLabel::setText);
    }

    private void startIntegratedCharge(BigDecimal total) {
        subtitleLabel.setText("Cobrança automática na maquininha");
        confirmButton.setVisible(false);
        confirmButton.setManaged(false);
        statusLabel.setText("Enviando a cobrança para a maquininha...");
        poller = new CardChargePoller(gateway, statusLabel::setText, this::approve, this::backToSelection);
        statusLabel.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null && poller != null) {
                poller.stop();
            }
        });
        poller.start(() -> gateway.createCharge(total, cardType));
    }

    private void approve() {
        if (done) {
            return;
        }
        done = true;
        Sale finalizada = saleService.payCard(vendaAtual, cardType);
        try {
            receiptService.printSaleReceipt(finalizada);
        } catch (Exception e) {
            statusLabel.setText("Venda gravada. Aviso: impressão falhou (" + e.getMessage() + ")");
        }
        userSession.setUltimaVenda(finalizada);
        userSession.setUltimaForma(PaymentMethod.CARTAO);
        userSession.setUltimoTroco(null);
        userSession.setVendaAtual(null);
        navigator.navigate(Page.VENDA_FINALIZADA);
    }

    private void backToSelection(String message) {
        done = true;
        statusLabel.setText(message);
        PauseTransition pause = new PauseTransition(Duration.seconds(2.5));
        pause.setOnFinished(e -> navigator.navigate(Page.PAGAMENTO));
        pause.play();
    }
}
