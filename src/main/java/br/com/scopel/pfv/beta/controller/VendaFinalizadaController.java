package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.BackgroundPrinting;
import br.com.scopel.pfv.beta.controller.support.SplitPaymentView;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.ReceiptPayment;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class VendaFinalizadaController {

    private final UserSession userSession;
    private final Navigator navigator;
    private final ReceiptService receiptService;
    private final SplitPaymentService splitPaymentService;

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    @FXML private Label totalLabel;
    @FXML private Label methodLabel;
    @FXML private Label changeCaption;
    @FXML private Label changeLabel;
    @FXML private Label copyStatusLabel;
    @FXML private Node novaVendaButton;

    public VendaFinalizadaController(UserSession userSession, Navigator navigator, ReceiptService receiptService,
                                     SplitPaymentService splitPaymentService) {
        this.userSession = userSession;
        this.navigator = navigator;
        this.receiptService = receiptService;
        this.splitPaymentService = splitPaymentService;
    }

    @FXML
    public void initialize() {
        Sale venda = userSession.getUltimaVenda();
        PaymentMethod forma = userSession.getUltimaForma();
        BigDecimal troco = userSession.getUltimoTroco();

        totalLabel.setText("R$ " + MONEY.format(venda == null ? BigDecimal.ZERO : venda.getTotal()));
        methodLabel.setText(forma == null && venda != null ? splitPartsDescription(venda) : formaLabel(forma));

        boolean mostrarTroco = (forma == PaymentMethod.DINHEIRO || forma == null) && troco != null && troco.signum() > 0;
        changeCaption.setVisible(mostrarTroco);
        changeCaption.setManaged(mostrarTroco);
        changeLabel.setVisible(mostrarTroco);
        changeLabel.setManaged(mostrarTroco);
        if (mostrarTroco) {
            changeLabel.setText("R$ " + MONEY.format(troco));
        }

        Platform.runLater(() -> {
            if (novaVendaButton != null) {
                novaVendaButton.requestFocus();
            }
        });
    }

    @FXML
    public void onKey(KeyEvent e) {
        if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
            onNovaVenda();
            e.consume();
        }
    }

    @FXML
    public void onNovaVenda() {
        navigator.navigate(Page.VENDA);
    }

    @FXML
    public void onInicio() {
        navigator.navigate(Page.INICIO);
    }

    @FXML
    public void onEmitirNovaVia() {
        Sale venda = userSession.getUltimaVenda();
        if (venda == null) {
            copyStatusLabel.setText("Não há venda recente para emitir a via.");
            return;
        }
        copyStatusLabel.setText("Enviando a 2ª via para a impressora...");
        Runnable printing = userSession.getUltimaForma() == null
                ? splitCopyPrinting(venda)
                : singleCopyPrinting(venda);
        BackgroundPrinting.run(printing, message -> copyStatusLabel.setText("Falha ao imprimir a 2ª via: " + message));
        copyStatusLabel.setText("2ª via enviada para a impressora.");
    }

    private Runnable splitCopyPrinting(Sale venda) {
        List<ReceiptPayment> parts = splitPaymentService.receiptPayments(venda);
        return () -> receiptService.printSplitSaleReceiptCopy(venda, parts);
    }

    private Runnable singleCopyPrinting(Sale venda) {
        ReceiptPayment payment = paymentOf(venda);
        return () -> receiptService.printSaleReceiptCopy(venda, payment);
    }

    private String splitPartsDescription(Sale venda) {
        return splitPaymentService.receiptPayments(venda).stream()
                .map(part -> SplitPaymentView.describe(part.method(), part.cardType(), part.received(), part.change()))
                .collect(Collectors.joining("\n"));
    }

    private ReceiptPayment paymentOf(Sale venda) {
        PaymentMethod forma = userSession.getUltimaForma();
        if (forma == null) {
            return null;
        }
        return switch (forma) {
            case DINHEIRO -> {
                BigDecimal troco = userSession.getUltimoTroco() == null ? BigDecimal.ZERO : userSession.getUltimoTroco();
                yield ReceiptPayment.cash(venda.getTotal().add(troco), troco);
            }
            case CARTAO -> ReceiptPayment.card(userSession.getCardTypeSelecionado());
            case PIX -> ReceiptPayment.pix();
            case PRAZO -> new ReceiptPayment(PaymentMethod.PRAZO, null, null, null);
        };
    }

    private String formaLabel(PaymentMethod forma) {
        if (forma == null) {
            return "PAGAMENTO DIVIDIDO";
        }
        return switch (forma) {
            case DINHEIRO -> "DINHEIRO";
            case CARTAO -> "CARTÃO";
            case PIX -> "PIX";
            case PRAZO -> "A PRAZO (CREDIÁRIO)";
        };
    }
}
