package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.ChangeNotAvailableException;
import br.com.scopel.pfv.beta.exception.CreditLimitExceededException;
import br.com.scopel.pfv.beta.exception.CustomerCreditBlockedException;
import br.com.scopel.pfv.beta.exception.FinancialOperationException;
import br.com.scopel.pfv.beta.exception.InsufficientPaymentException;
import br.com.scopel.pfv.beta.exception.PaymentMethodNotAvailableException;
import br.com.scopel.pfv.beta.exception.PixNotConfiguredException;
import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.PaymentInput;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.CreditSaleService;
import br.com.scopel.pfv.beta.service.PixService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PaymentController {

    private final SaleService saleService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final PixService pixService;
    private final CreditSaleService creditSaleService;
    private final ReceiptService receiptService;
    private final PrinterPort printer;

    private Sale vendaAtual;

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    @FXML private ComboBox<PaymentMethod> methodBox;
    @FXML private Label totalLabel;
    @FXML private TextField amountReceivedField;
    @FXML private Label changeLabel;
    @FXML private Label statusLabel;

    // Pagamento múltiplo (2ª forma), PIX e crediário. Opcionais no FXML.
    @FXML private ComboBox<PaymentMethod> methodBox2;
    @FXML private TextField amount2Field;
    @FXML private TextArea pixArea;
    @FXML private TextField installmentsField;
    @FXML private DatePicker firstDueField;

    public PaymentController(SaleService saleService, UserSession userSession, Navigator navigator,
                             PixService pixService, CreditSaleService creditSaleService,
                             ReceiptService receiptService, PrinterPort printer) {
        this.saleService = saleService;
        this.userSession = userSession;
        this.navigator = navigator;
        this.pixService = pixService;
        this.creditSaleService = creditSaleService;
        this.receiptService = receiptService;
        this.printer = printer;
    }

    @FXML
    public void initialize() {
        methodBox.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
        methodBox.getSelectionModel().select(PaymentMethod.DINHEIRO);
        if (methodBox2 != null) {
            methodBox2.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
        }

        vendaAtual = userSession.getVendaAtual();
        totalLabel.setText(formatMoney(vendaAtual.getTotal()));
        changeLabel.setText(formatMoney(BigDecimal.ZERO));

        amountReceivedField.textProperty().addListener((obs, oldValue, newValue) -> updateChange());

        Platform.runLater(() -> amountReceivedField.requestFocus());
    }

    @FXML
    public void onConfirm() {
        try {
            BigDecimal received = parseAmount(amountReceivedField.getText());
            PaymentMethod method = methodBox.getValue();

            saleService.pay(vendaAtual, method, received);

            printAndOpenDrawer(method == PaymentMethod.DINHEIRO);

            userSession.setVendaAtual(null);

            navigator.navigate(Page.INICIO);

        } catch (NumberFormatException e) {
            statusLabel.setText("Valor inválido");
        } catch (InsufficientPaymentException e) {
            statusLabel.setText("Valor recebido menor que o total");
        } catch (PaymentMethodNotAvailableException e) {
            statusLabel.setText("Forma indisponível");
        }
    }

    /** Gera o "PIX Copia e Cola" do total (tecla F12 no PDV Prático). */
    @FXML
    public void onGeneratePix() {
        try {
            String payload = pixService.buildFromSettings(vendaAtual.getTotal(), "VENDA" + vendaAtual.getId());
            if (pixArea != null) {
                pixArea.setText(payload);
            }
            statusLabel.setText("QR PIX gerado (copie o código)");
        } catch (PixNotConfiguredException e) {
            statusLabel.setText("Configure a chave PIX em Configurações");
        }
    }

    /** Pagamento dividido em duas formas (RN18). */
    @FXML
    public void onPayMixed() {
        try {
            List<PaymentInput> inputs = new ArrayList<>();
            inputs.add(new PaymentInput(methodBox.getValue(), parseAmount(amountReceivedField.getText())));
            if (amount2Field != null && amount2Field.getText() != null && !amount2Field.getText().isBlank()) {
                inputs.add(new PaymentInput(methodBox2.getValue(), parseAmount(amount2Field.getText())));
            }
            saleService.payMixed(vendaAtual, inputs);
            boolean hasCash = inputs.stream().anyMatch(i -> i.method() == PaymentMethod.DINHEIRO);
            printAndOpenDrawer(hasCash);
            userSession.setVendaAtual(null);
            navigator.navigate(Page.INICIO);
        } catch (NumberFormatException e) {
            statusLabel.setText("Valor inválido");
        } catch (InsufficientPaymentException e) {
            statusLabel.setText("Soma das formas menor que o total");
        } catch (ChangeNotAvailableException e) {
            statusLabel.setText("Troco só sai do dinheiro");
        }
    }

    /** Fecha a venda a prazo / crediário. */
    @FXML
    public void onCredit() {
        try {
            int installments = Integer.parseInt(installmentsField.getText().trim());
            LocalDate firstDue = firstDueField.getValue();
            if (firstDue == null) {
                statusLabel.setText("Informe o vencimento da 1ª parcela");
                return;
            }
            creditSaleService.sellOnCredit(vendaAtual, installments, firstDue, 30);
            userSession.setVendaAtual(null);
            navigator.navigate(Page.INICIO);
        } catch (NumberFormatException e) {
            statusLabel.setText("Nº de parcelas inválido");
        } catch (CustomerCreditBlockedException e) {
            statusLabel.setText("Cliente bloqueado para crediário");
        } catch (CreditLimitExceededException e) {
            statusLabel.setText("Limite de crédito excedido");
        } catch (FinancialOperationException e) {
            statusLabel.setText(e.getMessage());
        }
    }

    /**
     * Imprime o cupom e abre a gaveta quando o pagamento tem dinheiro (RN11).
     * Falha de impressão/gaveta NÃO desfaz a venda já gravada (RNF06).
     */
    private void printAndOpenDrawer(boolean cash) {
        try {
            receiptService.printSaleReceipt(vendaAtual);
            if (cash) {
                printer.openDrawer();
            }
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
        return "R$ " + MONEY.format(value);
    }
}
