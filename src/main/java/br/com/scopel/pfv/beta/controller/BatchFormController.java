package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.service.ProductBatchService;
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

    @FXML private TextField codeField;
    @FXML private TextField batchCodeField;
    @FXML private TextField quantityField;
    @FXML private DatePicker expiryPicker;
    @FXML private Label messageLabel;

    public BatchFormController(ProductBatchService batchService) {
        this.batchService = batchService;
    }

    @FXML
    public void initialize() {
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

            batchService.registerBatch(codeField.getText().trim(), batchCodeField.getText(), expiry, qty);

            messageLabel.setText("Lote registrado!");
            codeField.clear();
            batchCodeField.clear();
            quantityField.clear();
            expiryPicker.setValue(null);
        } catch (NumberFormatException e) {
            messageLabel.setText("Quantidade inválida");
        } catch (ProductNotfindException e) {
            messageLabel.setText("Produto não encontrado");
        } catch (Exception e) {
            messageLabel.setText("Verifique os campos e tente novamente");
        }
    }
}
