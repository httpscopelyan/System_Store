package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.BetaApplication;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.InsufficientPaymentException;
import br.com.scopel.pfv.beta.exception.PaymentMethodNotAvailableException;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@Component
public class PaymentController {

    private final SaleService saleService;
    private final UserSession userSession;

    private Sale vendaAtual;

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    @FXML private ComboBox<PaymentMethod> methodBox;
    @FXML private Label totalLabel;
    @FXML private TextField amountReceivedField;
    @FXML private Label changeLabel;
    @FXML private Label statusLabel;

    public PaymentController(SaleService saleService, UserSession userSession) {
        this.saleService = saleService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        methodBox.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
        methodBox.getSelectionModel().select(PaymentMethod.DINHEIRO);

        vendaAtual = userSession.getVendaAtual();
        totalLabel.setText(formatMoney(vendaAtual.getTotal()));
        changeLabel.setText(formatMoney(BigDecimal.ZERO));

        amountReceivedField.textProperty().addListener((obs, oldValue, newValue) -> updateChange());

        amountReceivedField.requestFocus();
    }

    @FXML
    public void onConfirm() {
        try {
            BigDecimal received = parseAmount(amountReceivedField.getText());
            PaymentMethod method = methodBox.getValue();

            saleService.pay(vendaAtual, method, received);

            userSession.setVendaAtual(null);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/inicio.fxml"));
            loader.setControllerFactory(BetaApplication.springContext::getBean);
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) amountReceivedField.getScene().getWindow();
            stage.setScene(scene);

        } catch (NumberFormatException e) {
            statusLabel.setText("Valor inválido");
        } catch (InsufficientPaymentException e) {
            statusLabel.setText("Valor recebido menor que o total");
        } catch (PaymentMethodNotAvailableException e) {
            statusLabel.setText("Forma indisponível");
        } catch (IOException e) {
            throw new RuntimeException(e);
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
