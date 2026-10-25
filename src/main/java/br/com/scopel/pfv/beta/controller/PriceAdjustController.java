package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.service.ProductService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PriceAdjustController {

    private final ProductService productService;

    @FXML private TextField percentField;
    @FXML private TextField categoryField;
    @FXML private Label messageLabel;

    public PriceAdjustController(ProductService productService) {
        this.productService = productService;
    }

    @FXML
    public void onApply() {
        try {
            BigDecimal percent = new BigDecimal(percentField.getText().trim().replace(',', '.'));
            String category = categoryField.getText();
            int changed = productService.adjustPricesByPercent(percent, category);
            messageLabel.setText(changed + " produto(s) reajustado(s).");
        } catch (NumberFormatException e) {
            messageLabel.setText("Percentual inválido");
        } catch (Exception e) {
            messageLabel.setText("Não foi possível aplicar o ajuste");
        }
    }
}
