package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Confirmação manual do pagamento em cartão.
 *
 * <p>A INTEGRAÇÃO com a maquininha (Mercado Pago Point) está DESLIGADA (ADR
 * 0007/0013 — F4). O PDV não aciona nem pollа a máquina: o operador passa o
 * cartão na maquininha física e, quando ela aprovar, confirma aqui (Enter).
 * A confirmação chama {@code payCard}, que grava o {@code Payment} e baixa o
 * estoque. O código do polling com o {@code CardPaymentGateway} está comentado
 * no fim desta classe para quando a integração voltar.</p>
 */
@Component
public class PagamentoEsperaController {

    private final SaleService saleService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final ReceiptService receiptService;

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    private Sale vendaAtual;
    private CardType cardType;
    private boolean done;

    @FXML private Label totalLabel;
    @FXML private Label typeLabel;
    @FXML private Label statusLabel;

    public PagamentoEsperaController(SaleService saleService, UserSession userSession, Navigator navigator,
                                     ReceiptService receiptService) {
        this.saleService = saleService;
        this.userSession = userSession;
        this.navigator = navigator;
        this.receiptService = receiptService;
    }

    @FXML
    public void initialize() {
        vendaAtual = userSession.getVendaAtual();
        cardType = userSession.getCardTypeSelecionado();
        done = false;

        BigDecimal total = vendaAtual == null ? BigDecimal.ZERO : vendaAtual.getTotal();
        totalLabel.setText("R$ " + MONEY.format(total));
        typeLabel.setText(cardType == null ? "-" : cardType.name());

        if (vendaAtual == null || cardType == null) {
            statusLabel.setText("Dados da cobrança ausentes.");
            return;
        }
        statusLabel.setText("Passe o cartão na maquininha. Aprovou? Pressione ENTER para confirmar.");

        // --- INTEGRAÇÃO COM A MAQUININHA (DESLIGADA — religar na F4) ---
        // CardChargeResult result = gateway.createCharge(total, cardType);
        // chargeId = result.chargeId();
        // poller = new Timeline(new KeyFrame(Duration.millis(500), e -> poll()));
        // poller.setCycleCount(Animation.INDEFINITE);
        // statusLabel.sceneProperty().addListener((o, oldS, newS) -> {
        //     if (newS == null && poller != null) { poller.stop(); }
        // });
        // poller.play();
    }

    @FXML
    public void onKey(KeyEvent e) {
        switch (e.getCode()) {
            case ENTER -> { onConfirm(); e.consume(); }
            case ESCAPE -> { onCancel(); e.consume(); }
            default -> { /* ignora */ }
        }
    }

    /** Operador confirma que a maquininha aprovou (substitui o polling). */
    @FXML
    public void onConfirm() {
        if (done || vendaAtual == null || cardType == null) {
            return;
        }
        approve();
    }

    private void approve() {
        done = true;
        Sale finalizada = saleService.payCard(vendaAtual, cardType);
        try {
            receiptService.printSaleReceipt(finalizada); // cartão não abre gaveta
        } catch (Exception e) {
            statusLabel.setText("Venda gravada. Aviso: impressão falhou (" + e.getMessage() + ")");
        }
        userSession.setUltimaVenda(finalizada);
        userSession.setUltimaForma(PaymentMethod.CARTAO);
        userSession.setUltimoTroco(null);
        userSession.setVendaAtual(null);
        navigator.navigate(Page.VENDA_FINALIZADA);
    }

    @FXML
    public void onCancel() {
        if (done) {
            return;
        }
        done = true;
        // Sem integração não há cobrança a cancelar na máquina.
        // if (chargeId != null) { gateway.cancel(chargeId); }
        navigator.navigate(Page.PAGAMENTO);
    }

    // --- POLLING DA MAQUININHA (DESLIGADO — religar junto com o CardPaymentGateway na F4) ---
    // private String chargeId;
    // private Timeline poller;
    //
    // private void poll() {
    //     if (done || chargeId == null) {
    //         return;
    //     }
    //     CardPaymentStatus status = gateway.getStatus(chargeId);
    //     switch (status) {
    //         case APPROVED -> approve();
    //         case DECLINED -> backToSelection("Pagamento recusado pela maquininha.");
    //         case CANCELED -> backToSelection("Cobrança cancelada.");
    //         case PENDING -> { /* continua aguardando */ }
    //     }
    // }
    //
    // private void backToSelection(String message) {
    //     done = true;
    //     stopPoller();
    //     statusLabel.setText(message);
    //     PauseTransition pause = new PauseTransition(Duration.seconds(1.6));
    //     pause.setOnFinished(e -> navigator.navigate(Page.PAGAMENTO));
    //     pause.play();
    // }
    //
    // private void stopPoller() {
    //     if (poller != null) {
    //         poller.stop();
    //     }
    // }
}
