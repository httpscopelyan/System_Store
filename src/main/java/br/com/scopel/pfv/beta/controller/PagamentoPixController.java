package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.PixNotConfiguredException;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.PixService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Pagamento via PIX (Item 5): gera o "copia e cola" estático na entrada e espera
 * a CONFIRMAÇÃO MANUAL do recebimento pelo operador (PIX estático não confirma
 * sozinho). Confirmar finaliza a venda ({@code payPix}) e imprime o cupom.
 */
@Component
public class PagamentoPixController {

    private final SaleService saleService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final PixService pixService;
    private final ReceiptService receiptService;

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    private Sale vendaAtual;

    @FXML private Label totalLabel;
    @FXML private TextArea pixArea;
    @FXML private Label statusLabel;
    @FXML private Button confirmButton;

    public PagamentoPixController(SaleService saleService, UserSession userSession, Navigator navigator,
                                  PixService pixService, ReceiptService receiptService) {
        this.saleService = saleService;
        this.userSession = userSession;
        this.navigator = navigator;
        this.pixService = pixService;
        this.receiptService = receiptService;
    }

    @FXML
    public void initialize() {
        vendaAtual = userSession.getVendaAtual();
        BigDecimal total = vendaAtual == null ? BigDecimal.ZERO : vendaAtual.getTotal();
        totalLabel.setText("R$ " + MONEY.format(total));
        statusLabel.setText("");

        boolean ok = generatePix();
        if (confirmButton != null) {
            confirmButton.setDisable(!ok);
        }
        Platform.runLater(() -> {
            if (confirmButton != null) {
                confirmButton.requestFocus();
            }
        });
    }

    @FXML
    public void onKey(KeyEvent e) {
        if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
            navigator.navigate(Page.PAGAMENTO);
            e.consume();
        }
    }

    @FXML
    public void onVoltar() {
        navigator.navigate(Page.PAGAMENTO);
    }

    private boolean generatePix() {
        if (vendaAtual == null) {
            return false;
        }
        try {
            String payload = pixService.buildFromSettings(vendaAtual.getTotal(), "VENDA" + vendaAtual.getId());
            if (pixArea != null) {
                pixArea.setText(payload);
            }
            statusLabel.setText("Mostre o QR / copie o código e confirme ao receber.");
            return true;
        } catch (PixNotConfiguredException e) {
            if (pixArea != null) {
                pixArea.clear();
            }
            statusLabel.setText("Configure a chave PIX em Configurações");
            return false;
        }
    }

    @FXML
    public void onConfirm() {
        if (vendaAtual == null) return;
        Sale finalizada = saleService.payPix(vendaAtual);
        try {
            receiptService.printSaleReceipt(finalizada); // PIX não abre gaveta
        } catch (Exception e) {
            statusLabel.setText("Venda gravada. Aviso: impressão falhou (" + e.getMessage() + ")");
        }
        userSession.setUltimaVenda(finalizada);
        userSession.setUltimaForma(PaymentMethod.PIX);
        userSession.setUltimoTroco(null);
        userSession.setVendaAtual(null);
        navigator.navigate(Page.VENDA_FINALIZADA);
    }
}
