package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.BetaApplication;
import br.com.scopel.pfv.beta.config.ThemeManager;
import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Diálogo modal de cadastro rápido de produto, aberto quando o operador bipa um
 * código inexistente na tela de venda (decisão do dev / RNF03: foco permanece no
 * fluxo da venda, sem navegar para a página PRODUTOS).
 *
 * <p>O código vem travado (read-only) com o valor bipado CRU — String, nunca
 * parseLong (inviolável 2 / RN02: preserva zeros à esquerda). O operador informa
 * nome, preço (BigDecimal, HALF_UP, aceita vírgula pt-BR) e a unidade (UN/KG). O
 * produto é salvo com estoque 0 pelo mesmo {@link ProductService#save} do cadastro
 * completo (não duplica a validação de duplicado). Depois o operador REBIPA para
 * vender — o preço só é congelado no item no momento da venda (RN19).
 */
@Component
public class QuickProductController {

    private final ProductService productService;
    private final ThemeManager themeManager;

    @FXML private TextField codeField;
    @FXML private TextField descriptionField;
    @FXML private TextField priceField;
    @FXML private ComboBox<Product.Unidade> unitBox;
    @FXML private Label errorLabel;

    private Stage dialogStage;
    private Product registeredProduct;
    private String barcode;

    public QuickProductController(ProductService productService, ThemeManager themeManager) {
        this.productService = productService;
        this.themeManager = themeManager;
    }

    @FXML
    public void initialize() {
        unitBox.getItems().setAll(Product.Unidade.values());
        unitBox.setValue(Product.Unidade.UN);
        codeField.setText(barcode == null ? "" : barcode);
        codeField.setEditable(false);
        errorLabel.setText("");
        Platform.runLater(() -> descriptionField.requestFocus());
    }

    /**
     * Abre o diálogo modal sobre a janela informada, já com o código bipado, e
     * bloqueia até o desfecho. Retorna o produto recém-cadastrado quando salvo,
     * ou vazio quando o operador cancela (Esc / botão Cancelar).
     */
    public Optional<Product> requestQuickRegister(Window ownerWindow, String biphedBarcode) {
        this.barcode = biphedBarcode;
        this.registeredProduct = null;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/cadastro_rapido.fxml"));
        loader.setControllerFactory(BetaApplication.springContext::getBean);
        Parent dialogRoot;
        try {
            dialogRoot = loader.load();
        } catch (IOException loadError) {
            throw new RuntimeException("Falha ao abrir o cadastro rápido de produto", loadError);
        }

        themeManager.apply(dialogRoot);

        dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        if (ownerWindow != null) {
            dialogStage.initOwner(ownerWindow);
        }
        dialogStage.setTitle("Cadastro rápido de produto");
        dialogStage.setResizable(false);

        Scene dialogScene = new Scene(dialogRoot);
        dialogScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                onCancel();
            }
        });
        dialogStage.setScene(dialogScene);
        dialogStage.showAndWait();

        return Optional.ofNullable(registeredProduct);
    }

    @FXML
    public void onConfirm() {
        String description = descriptionField.getText();
        if (description == null || description.isBlank()) {
            showError("Informe o nome do produto");
            return;
        }

        BigDecimal price = parsePrice(priceField.getText());
        if (price == null) {
            showError("Preço inválido");
            return;
        }

        Product.Unidade unit = unitBox.getValue();
        if (unit == null) {
            showError("Selecione a unidade");
            return;
        }

        try {
            registeredProduct = productService.save(buildProduct(description.trim(), price, unit));
            closeDialog();
        } catch (RegisterProductDuplicateException duplicate) {
            showError("Este código já está cadastrado");
        }
    }

    @FXML
    public void onCancel() {
        registeredProduct = null;
        closeDialog();
    }

    /** Monta o produto novo: código bipado cru, estoque 0 e demais campos no default. */
    private Product buildProduct(String description, BigDecimal price, Product.Unidade unit) {
        Product product = new Product();
        product.setCode(barcode);
        product.setDescription(description);
        product.setPrice(price);
        product.setUnit(unit);
        product.setQuantity(BigDecimal.ZERO);
        product.setActive(true);
        return product;
    }

    /** Preço em BigDecimal com vírgula pt-BR, normalizado em 2 casas (HALF_UP). Negativo/ inválido = null. */
    private BigDecimal parsePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(rawPrice.trim().replace(',', '.'));
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            return price.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException invalidNumber) {
            return null;
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }
}
