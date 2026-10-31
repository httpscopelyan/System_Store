package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.controller.support.EscapeToBack;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

@Component
public class PriceCheckController {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    private final ProductService productService;
    private final Navigator navigator;

    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private Label descriptionLabel;
    @FXML private Label priceLabel;
    @FXML private Label unitLabel;
    @FXML private Label statusLabel;

    public PriceCheckController(ProductService productService, Navigator navigator) {
        this.productService = productService;
        this.navigator = navigator;
    }

    @FXML
    public void initialize() {
        clearResult();
        statusLabel.setText("");
        EscapeToBack.install(codeField, navigator);
        focusCode();
    }

    @FXML
    public void onSearch(ActionEvent event) {
        String code = codeField.getText() == null ? "" : codeField.getText().trim();
        if (!code.isEmpty()) {
            searchByCode(code);
            return;
        }
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (!name.isEmpty()) {
            searchByName(name);
            return;
        }
        clearResult();
        statusLabel.setText("Informe um código ou um nome para consultar");
    }

    public void searchByCode(String code) {
        try {
            showProduct(productService.findCode(code));
        } catch (ProductNotfindException productNotFound) {
            clearResult();
            statusLabel.setText("Produto não encontrado");
        }
        focusCode();
    }

    public void searchByName(String name) {
        List<Product> matches = productService.searchByName(name);
        if (matches.isEmpty()) {
            clearResult();
            statusLabel.setText("Produto não encontrado");
            return;
        }
        showProduct(matches.get(0));
        if (matches.size() > 1) {
            statusLabel.setText("Mostrando o 1º de " + matches.size() + " resultados — refine o nome");
        }
    }

    private void showProduct(Product product) {
        descriptionLabel.setText(product.getDescription());
        priceLabel.setText("R$ " + MONEY.format(product.getPrice()));
        unitLabel.setText("Unidade: " + product.getUnit() + "   Código: " + product.getCode());
        statusLabel.setText("");
    }

    private void clearResult() {
        descriptionLabel.setText("—");
        priceLabel.setText("R$ 0,00");
        unitLabel.setText("");
    }

    @FXML
    public void onBack() {
        navigator.back();
    }

    private void focusCode() {
        Platform.runLater(() -> {
            if (codeField.getScene() != null) {
                codeField.requestFocus();
                codeField.selectAll();
            }
        });
    }
}
