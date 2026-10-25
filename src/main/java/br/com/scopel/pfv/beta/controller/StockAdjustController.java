package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.StockService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StockAdjustController {

    private final StockService stockService;
    private final UserSession userSession;

    @FXML private TextField codeField;
    @FXML private TextField quantityField;
    @FXML private TextField reasonField;
    @FXML private Label messageLabel;

    public StockAdjustController(StockService stockService, UserSession userSession) {
        this.stockService = stockService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        Platform.runLater(() -> codeField.requestFocus());
    }

    @FXML
    public void onSave() {
        try {
            BigDecimal newQty = new BigDecimal(quantityField.getText().trim().replace(',', '.'));
            String reason = reasonField.getText();
            if (reason == null || reason.isBlank()) {
                messageLabel.setText("Informe o motivo do ajuste");
                return;
            }
            Product p = stockService.adjust(codeField.getText().trim(), newQty, reason,
                    userSession.getLoggedUser());
            messageLabel.setText("Estoque ajustado para " + p.getQuantity().toPlainString());
            codeField.clear();
            quantityField.clear();
            reasonField.clear();
        } catch (NumberFormatException e) {
            messageLabel.setText("Quantidade inválida");
        } catch (ProductNotfindException e) {
            messageLabel.setText("Produto não encontrado");
        } catch (Exception e) {
            messageLabel.setText("Verifique os campos e tente novamente");
        }
    }
}
