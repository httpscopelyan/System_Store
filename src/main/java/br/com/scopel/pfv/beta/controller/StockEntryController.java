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
public class StockEntryController {

    private final StockService stockService;
    private final UserSession userSession;

    @FXML private TextField codeField;
    @FXML private TextField quantityField;
    @FXML private TextField unitCostField;
    @FXML private TextField reasonField;
    @FXML private Label messageLabel;

    public StockEntryController(StockService stockService, UserSession userSession) {
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
            BigDecimal qty = new BigDecimal(quantityField.getText().trim().replace(',', '.'));
            String costText = unitCostField.getText();
            BigDecimal unitCost = (costText == null || costText.isBlank())
                    ? null : new BigDecimal(costText.trim().replace(',', '.'));

            Product p = stockService.registerEntry(codeField.getText().trim(), qty, unitCost,
                    reasonField.getText(), userSession.getLoggedUser());

            messageLabel.setText("Entrada registrada. Estoque atual: " + p.getQuantity().toPlainString());
            codeField.clear();
            quantityField.clear();
            unitCostField.clear();
            reasonField.clear();
            codeField.requestFocus();
        } catch (NumberFormatException e) {
            messageLabel.setText("Quantidade ou custo inválidos");
        } catch (ProductNotfindException e) {
            messageLabel.setText("Produto não encontrado");
        } catch (Exception e) {
            messageLabel.setText("Verifique os campos e tente novamente");
        }
    }
}
