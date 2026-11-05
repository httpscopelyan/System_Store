package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.controller.support.EnterToNextField;
import br.com.scopel.pfv.beta.controller.support.ProductCodeInput;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.DecimalInput;
import br.com.scopel.pfv.beta.controller.support.MoneyFormat;
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

    private final ProductService productService;
    @FXML private TextField codeField;
    @FXML private TextField quantityField;
    @FXML private TextField unitCostField;
    @FXML private TextField reasonField;
    @FXML private Label costPreviewLabel;
    @FXML private Label messageLabel;

    public StockEntryController(StockService stockService, UserSession userSession, ProductService productService) {
        this.productService = productService;
        this.stockService = stockService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        ProductCodeInput.attach(codeField, productService);
        EnterToNextField.install(codeField, quantityField, unitCostField, reasonField);
        codeField.textProperty().addListener((observable, previous, current) -> refreshCostPreview());
        quantityField.textProperty().addListener((observable, previous, current) -> refreshCostPreview());
        unitCostField.textProperty().addListener((observable, previous, current) -> refreshCostPreview());
        String prefillCode = userSession.consumeStockEntryPrefillCode();
        if (prefillCode != null) {
            codeField.setText(prefillCode);
            Platform.runLater(() -> quantityField.requestFocus());
            return;
        }
        Platform.runLater(() -> codeField.requestFocus());
    }

    @FXML
    public void onSave() {
        try {
            BigDecimal quantity = DecimalInput.parseRequired(quantityField.getText());
            BigDecimal unitCost = DecimalInput.parseOrNull(unitCostField.getText());
            if (!unitCostField.getText().isBlank() && unitCost == null) {
                throw new NumberFormatException("Custo unitário inválido");
            }
            Product product = stockService.registerEntry(productService.resolveCode(codeField.getText()), quantity, unitCost,
                    reasonField.getText(), userSession.getLoggedUser());
            messageLabel.setText("Entrada registrada. Estoque atual: " + product.getQuantity().toPlainString());
            codeField.clear();
            quantityField.clear();
            unitCostField.clear();
            reasonField.clear();
            codeField.requestFocus();
        } catch (NumberFormatException invalidNumber) {
            messageLabel.setText("Quantidade ou custo inválidos");
        } catch (IllegalArgumentException invalidValue) {
            messageLabel.setText(invalidValue.getMessage());
        } catch (ProductNotfindException notFound) {
            messageLabel.setText("Produto não encontrado");
        } catch (Exception unexpected) {
            messageLabel.setText("Verifique os campos e tente novamente");
        }
    }

    private void refreshCostPreview() {
        BigDecimal unitCost = DecimalInput.parseOrNull(unitCostField.getText());
        BigDecimal quantity = DecimalInput.parseOrNull(quantityField.getText());
        if (unitCost == null || codeField.getText().isBlank()) {
            costPreviewLabel.setText("");
            return;
        }
        try {
            StockService.CostPreview preview = stockService.previewEntryCost(productService.resolveCode(codeField.getText()), quantity, unitCost);
            costPreviewLabel.setText("Custo atual " + MoneyFormat.format(preview.currentCost())
                    + " → novo custo " + MoneyFormat.format(preview.newCost()) + " (" + preview.mode().label() + ")");
        } catch (ProductNotfindException | IllegalArgumentException notResolved) {
            costPreviewLabel.setText("");
        }
    }
}
