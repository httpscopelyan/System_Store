package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CreateProductsController {

    private final ProductService productService;

    @FXML private TextField codeField;
    @FXML private TextField descriptionField;
    @FXML private TextField priceField;
    @FXML private TextField quantityField;
    @FXML private ComboBox<Product.Unidade> unitBox;
    @FXML private CheckBox activeBox;
    @FXML private Label messageLabel;

    public CreateProductsController(ProductService productService) {
        this.productService = productService;
    }

    @FXML
    public void initialize() {
        unitBox.getItems().addAll(Product.Unidade.values());
    }

    @FXML
    public void onSave() {
        try {
            String codeText = codeField.getText();
            String descriptionText = descriptionField.getText();
            BigDecimal priceValue = new BigDecimal(priceField.getText());
            BigDecimal quantityValue = new BigDecimal(quantityField.getText());
            Product.Unidade unitValue = unitBox.getValue();
            Boolean activeState = activeBox.isSelected();

            Product product = new Product();
            product.setCode(codeText);
            product.setDescription(descriptionText);
            product.setPrice(priceValue);
            product.setQuantity(quantityValue);
            product.setUnit(unitValue);
            product.setActive(activeState);

            productService.save(product);
            messageLabel.setText("Produto cadastrado!");

            codeField.clear();
            descriptionField.clear();
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