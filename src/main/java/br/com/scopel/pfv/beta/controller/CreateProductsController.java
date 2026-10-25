package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class CreateProductsController {

    private final ProductService productService;

    @FXML
    private TextField codeField;
    @FXML
    private TextField descriptionField;
    @FXML
    private TextField costField;
    @FXML
    private TextField margemField;
    @FXML
    private TextField priceField;
    @FXML
    private TextField quantityField;
    @FXML
    private ComboBox<Product.Unidade> unitBox;
    @FXML
    private CheckBox activeBox;
    @FXML
    private Label messageLabel;

    public CreateProductsController(ProductService productService) {
        this.productService = productService;
    }

    @FXML
    public void initialize() {
        unitBox.getItems().addAll(Product.Unidade.values());
        // Modo markup: custo + margem calculam o preço. Margem em branco = preço direto.
        costField.textProperty().addListener((o, a, b) -> applyMargem());
        margemField.textProperty().addListener((o, a, b) -> applyMargem());
        // Foco no campo de código ao abrir a tela (digitação/teclado pronto).
        Platform.runLater(() -> codeField.requestFocus());
    }

    /** Com custo + margem válidos, calcula o preço (custo × (1 + margem/100)). */
    private void applyMargem() {
        BigDecimal cost = parseOrNull(costField.getText());
        BigDecimal margem = parseOrNull(margemField.getText());
        if (cost == null || cost.compareTo(BigDecimal.ZERO) < 0
                || margem == null || margemField.getText().isBlank()) {
            return;
        }
        BigDecimal factor = BigDecimal.ONE.add(margem.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        priceField.setText(cost.multiply(factor).setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private BigDecimal parseOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @FXML
    public void onSave() {
        try {
            String codeText = codeField.getText();
            String descriptionText = descriptionField.getText();
            BigDecimal priceValue = new BigDecimal(priceField.getText()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal quantityValue = new BigDecimal(quantityField.getText());
            Product.Unidade unitValue = unitBox.getValue();
            Boolean activeState = activeBox.isSelected();

            // Custo é opcional no cadastro (default 0 quando não informado).
            BigDecimal costText = parseOrNull(costField.getText());
            BigDecimal costValue = (costText == null ? BigDecimal.ZERO : costText).setScale(2, RoundingMode.HALF_UP);

            Product product = new Product();
            product.setCode(codeText);
            product.setDescription(descriptionText);
            product.setCostPrice(costValue);
            product.setPrice(priceValue);
            product.setQuantity(quantityValue);
            product.setUnit(unitValue);
            product.setActive(activeState);

            productService.save(product);
            messageLabel.setText("Produto cadastrado!");

            codeField.clear();
            descriptionField.clear();
            costField.clear();
            margemField.clear();
            priceField.clear();
            quantityField.clear();
            unitBox.setValue(null);
            activeBox.setSelected(false);

        } catch (NumberFormatException e) {
            messageLabel.setText("Preço ou quantidade inválidos");
        } catch (RegisterProductDuplicateException e) {
            messageLabel.setText("Este código já está cadastrado");
        } catch (Exception e) {
            messageLabel.setText("Verifique os campos e tente novamente");
        }
    }
}