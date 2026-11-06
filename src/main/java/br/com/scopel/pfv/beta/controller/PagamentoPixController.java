package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.CardChargePoller;
import br.com.scopel.pfv.beta.exception.PixNotConfiguredException;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.PixService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@Component
public class PagamentoPixController {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    private final SaleService saleService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final PixService pixService;
    private final ReceiptService receiptService;
    private final CardPaymentGateway gateway;

    private Sale vendaAtual;
    private boolean done;
    private CardChargePoller poller;

    @FXML private Label totalLabel;
    @FXML private VBox copyPasteBox;
    @FXML private TextArea pixArea;
    @FXML private Label statusLabel;
    @FXML private Button confirmButton;

    public PagamentoPixController(SaleService saleService, UserSession userSession, Navigator navigator,
                                  PixService pixService, ReceiptService receiptService,
                                  CardPaymentGateway gateway) {
        this.saleService = saleService;
        this.userSession = userSession;
        this.navigator = navigator;
        this.pixService = pixService;
        this.receiptService = receiptService;
        this.gateway = gateway;
    }

    @FXML
    public void initialize() {
        vendaAtual = userSession.getVendaAtual();
        done = false;
        poller = null;
        BigDecimal total = vendaAtual == null ? BigDecimal.ZERO : vendaAtual.getTotal();
        totalLabel.setText("R$ " + MONEY.format(total));
        statusLabel.setText("");

        if (vendaAtual != null && gateway.isIntegrated()) {
            startMachineCharge(total);
            return;
        }
        boolean ok = generatePix();
        confirmButton.setDisable(!ok);
        Platform.runLater(confirmButton::requestFocus);
    }

    @FXML
    public void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE) {
            onVoltar();
            e.consume();
        }
    }

    @FXML
    public void onVoltar() {
        if (done) {
            return;
        }
        if (poller == null) {
            navigator.navigate(Page.PAGAMENTO);
            return;
        }
        statusLabel.setText("Cancelando a cobrança na maquininha...");
        poller.cancel(() -> {
            done = true;
            navigator.navigate(Page.PAGAMENTO);
        }, statusLabel::setText);
    }

    @FXML
    public void onConfirm() {
        if (vendaAtual == null || poller != null) {
            return;
        }
        approve();
    }

    private void startMachineCharge(BigDecimal total) {
        copyPasteBox.setVisible(false);
        copyPasteBox.setManaged(false);
        confirmButton.setVisible(false);
        confirmButton.setManaged(false);
        statusLabel.setText("Enviando a cobrança PIX para a maquininha...");
        poller = new CardChargePoller(gateway, statusLabel::setText, this::approve, this::backToSelection);
        statusLabel.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null && poller != null) {
                poller.stop();
            }
        });
        poller.start(() -> gateway.createPixCharge(total));
    }

    private boolean generatePix() {
        if (vendaAtual == null) {
            return false;
        }
        try {
            String payload = pixService.buildFromSettings(vendaAtual.getTotal(), "VENDA" + vendaAtual.getId());
            pixArea.setText(payload);
            statusLabel.setText("Copie o código e confirme ao receber.");
            return true;
        } catch (PixNotConfiguredException e) {
            pixArea.clear();
            statusLabel.setText("Configure a chave PIX em Configurações");
            return false;
        }
    }

    private void approve() {
        if (done) {
            return;
        }
        done = true;
        Sale finalizada = saleService.payPix(vendaAtual);
        try {
            receiptService.printSaleReceipt(finalizada);
        } catch (Exception e) {
            statusLabel.setText("Venda gravada. Aviso: impressão falhou (" + e.getMessage() + ")");
        }
        userSession.setUltimaVenda(finalizada);
        userSession.setUltimaForma(PaymentMethod.PIX);
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
