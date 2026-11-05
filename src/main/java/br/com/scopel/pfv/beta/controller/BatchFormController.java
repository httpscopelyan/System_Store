package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.controller.support.ProductCodeInput;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.service.ProductBatchService;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class BatchFormController {

    private final ProductBatchService batchService;
    private final ProductService productService;

    @FXML private TextField codeField;
    @FXML private TextField batchCodeField;
    @FXML private TextField quantityField;
    @FXML private DatePicker expiryPicker;
    @FXML private Label messageLabel;

    public BatchFormController(ProductBatchService batchService, ProductService productService) {
        this.batchService = batchService;
        this.productService = productService;
    }

    @FXML
    public void initialize() {
        ProductCodeInput.attach(codeField, productService);
        Platform.runLater(() -> codeField.requestFocus());
    }

    @FXML
    public void onSave() {
        try {
            LocalDate expiry = expiryPicker.getValue();
            if (expiry == null) {
                messageLabel.setText("Informe a data de validade");
                return;
            }
            BigDecimal qty = new BigDecimal(quantityField.getText().trim().replace(',', '.'));

            batchService.registerBatch(productService.resolveCode(codeField.getText()), batchCodeField.getText(), expiry, qty);

            messageLabel.setText("Lote registrado!");
            codeField.clear();
            batchCodeField.clear();
            quantityField.clear();
            expiryPicker.setValue(null);
        } catch (NumberFormatException e) {
            messageLabel.setText("Quantidade inválida");
        } catch (ProductNotfindException e) {
            messageLabel.setText("Produto não encontrado");
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
        } catch (Exception e) {
            messageLabel.setText("Verifique os campos e tente novamente");
        }
    }
}
