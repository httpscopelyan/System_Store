package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.LabelService;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

@Component
public class LabelFormController {

    private final ProductService productService;
    private final LabelService labelService;

    @FXML private TextField codeField;
    @FXML private TextField copiesField;
    @FXML private Label previewLabel;
    @FXML private Label messageLabel;

    public LabelFormController(ProductService productService, LabelService labelService) {
        this.productService = productService;
        this.labelService = labelService;
    }

    @FXML
    public void initialize() {
        Platform.runLater(() -> codeField.requestFocus());
    }

    @FXML
    public void onPreview() {
        try {
            Product p = productService.findCode(codeField.getText().trim());
            previewLabel.setText(labelService.buildLabel(p));
            messageLabel.setText("");
        } catch (ProductNotfindException e) {
            messageLabel.setText("Produto não encontrado");
        }
    }

    @FXML
    public void onPrint() {
        try {
            Product p = productService.findCode(codeField.getText().trim());
            int copies = Integer.parseInt(copiesField.getText().trim());
            if (copies < 1) {
                messageLabel.setText("Informe ao menos 1 cópia");
                return;
            }
            labelService.printLabels(p, copies);
            messageLabel.setText(copies + " etiqueta(s) enviada(s) à impressora");
        } catch (NumberFormatException e) {
            messageLabel.setText("Número de cópias inválido");
        } catch (ProductNotfindException e) {
            messageLabel.setText("Produto não encontrado");
        }
    }
}
