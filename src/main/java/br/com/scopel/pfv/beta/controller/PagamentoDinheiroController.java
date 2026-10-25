package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.InsufficientPaymentException;
import br.com.scopel.pfv.beta.exception.PaymentMethodNotAvailableException;
import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Pagamento em DINHEIRO (Item 5): valor recebido + troco ao vivo. Confirma via
 * SaleService.pay, imprime o cupom e abre a gaveta; registra os dados da venda
 * nos holders da sessão e vai para a tela "Venda finalizada".
 */
@Component
public class PagamentoDinheiroController {

    private final SaleService saleService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final ReceiptService receiptService;
    private final PrinterPort printer;

    private Sale vendaAtual;

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    @FXML private Label totalLabel;
    @FXML private TextField amountReceivedField;
    @FXML private Label changeLabel;
    @FXML private Label statusLabel;

    public PagamentoDinheiroController(SaleService saleService, UserSession userSession, Navigator navigator,
                                       ReceiptService receiptService, PrinterPort printer) {
        this.saleService = saleService;
        this.userSession = userSession;
        this.navigator = navigator;
        this.receiptService = receiptService;
        this.printer = printer;
    }

    @FXML
    public void initialize() {
        vendaAtual = userSession.getVendaAtual();
        totalLabel.setText(formatMoney(vendaAtual == null ? BigDecimal.ZERO : vendaAtual.getTotal()));
        changeLabel.setText(formatMoney(BigDecimal.ZERO));
        statusLabel.setText("");

        amountReceivedField.textProperty().addListener((obs, o, n) -> updateChange());
        Platform.runLater(amountReceivedField::requestFocus);
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

    @FXML
    public void onConfirm() {
        if (vendaAtual == null) return;
        try {
            BigDecimal received = parseAmount(amountReceivedField.getText());
            Sale finalizada = saleService.pay(vendaAtual, PaymentMethod.DINHEIRO, received);

            BigDecimal change = saleService.calculateChange(finalizada.getTotal(), received);
            if (change.signum() < 0) {
                change = BigDecimal.ZERO;
            }

            printAndOpenDrawer();

            userSession.setUltimaVenda(finalizada);
            userSession.setUltimaForma(PaymentMethod.DINHEIRO);
            userSession.setUltimoTroco(change);
            userSession.setVendaAtual(null);

            navigator.navigate(Page.VENDA_FINALIZADA);
        } catch (NumberFormatException e) {
            statusLabel.setText("Valor inválido");
        } catch (InsufficientPaymentException e) {
            statusLabel.setText("Valor recebido menor que o total");
        } catch (PaymentMethodNotAvailableException e) {
            statusLabel.setText("Forma indisponível");
        }
    }

    /** Cupom + gaveta (RN11). Falha de impressão NÃO desfaz a venda (RNF06). */
    private void printAndOpenDrawer() {
        try {
            receiptService.printSaleReceipt(vendaAtual);
            printer.openDrawer();
        } catch (Exception e) {
            statusLabel.setText("Venda gravada. Aviso: impressão/gaveta falhou (" + e.getMessage() + ")");
        }
    }

    private void updateChange() {
        try {
            BigDecimal received = parseAmount(amountReceivedField.getText());
            BigDecimal change = saleService.calculateChange(vendaAtual.getTotal(), received);
            if (change.signum() < 0) {
                change = BigDecimal.ZERO;
            }
            changeLabel.setText(formatMoney(change));
        } catch (NumberFormatException e) {
            changeLabel.setText(formatMoney(BigDecimal.ZERO));
        }
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.isBlank()) {
            throw new NumberFormatException("vazio");
        }
        return new BigDecimal(text.trim().replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal value) {
        return "R$ " + MONEY.format(value == null ? BigDecimal.ZERO : value);
    }
}
