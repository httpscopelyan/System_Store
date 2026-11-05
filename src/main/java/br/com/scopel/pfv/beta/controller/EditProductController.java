package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.controller.support.EnterToNextField;
import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.DecimalInput;
import br.com.scopel.pfv.beta.controller.support.SuggestedPriceBinding;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.PricingSuggestionService;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class EditProductController {

    private final ProductService productService;
    private final PricingSuggestionService pricingSuggestionService;
    private final Navigator navigator;
    private final UserSession userSession;

    private Long productId;

    @FXML private TextField codeField;
    @FXML private Label stockLabel;
    @FXML private TextField descriptionField;
    @FXML private ComboBox<Product.Unidade> unitBox;
    @FXML private TextField categoryField;
    @FXML private TextField costField;
    @FXML private TextField marginField;
    @FXML private TextField priceField;
    @FXML private TextField minStockField;
    @FXML private Label profitLabel;
    @FXML private CheckBox activeBox;
    @FXML private Label messageLabel;

    public EditProductController(ProductService productService,
                                 PricingSuggestionService pricingSuggestionService,
                                 Navigator navigator, UserSession userSession) {
        this.productService = productService;
        this.pricingSuggestionService = pricingSuggestionService;
        this.navigator = navigator;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        EnterToNextField.install(codeField, descriptionField, unitBox, categoryField, minStockField, costField, marginField,
                priceField);
        unitBox.getItems().setAll(Product.Unidade.values());

        productId = userSession.getProdutoEmEdicaoId();
        if (productId == null) {
            messageLabel.setText("Nenhum produto selecionado para edição.");
            return;
        }

        Product product = productService.findById(productId);
        codeField.setText(product.getCode());
        stockLabel.setText(formatQuantity(product.getQuantity()));
        descriptionField.setText(product.getDescription());
        unitBox.setValue(product.getUnit());
        categoryField.setText(product.getCategory());
        costField.setText(plain(product.getCostPrice()));
        minStockField.setText(formatQuantity(product.getMinStock()));
        activeBox.setSelected(Boolean.TRUE.equals(product.getActive()));

        SuggestedPriceBinding priceBinding =
                new SuggestedPriceBinding(costField, marginField, priceField, pricingSuggestionService, false);
        priceBinding.loadPrice(plain(product.getPrice()));

        costField.textProperty().addListener((observable, previous, current) -> refreshProfit());
        priceField.textProperty().addListener((observable, previous, current) -> refreshProfit());
        refreshProfit();

        Platform.runLater(() -> codeField.requestFocus());
    }

    @FXML
    public void onSave() {
        if (productId == null) {
            messageLabel.setText("Nenhum produto selecionado para edição.");
            return;
        }
        try {
            productService.update(
                    productId,
                    codeField.getText(),
                    descriptionField.getText(),
                    unitBox.getValue(),
                    DecimalInput.parseRequired(priceField.getText()),
                    DecimalInput.parseRequired(costField.getText()),
                    DecimalInput.parseRequired(minStockField.getText()),
                    categoryField.getText(),
                    activeBox.isSelected());
            userSession.setProdutoEmEdicaoId(null);
            navigator.navigate(Page.ESTOQUE);
        } catch (NumberFormatException invalidNumber) {
            messageLabel.setText("Custo, preço ou estoque mínimo inválidos.");
        } catch (IllegalArgumentException invalidField) {
            messageLabel.setText(invalidField.getMessage());
        } catch (Exception unexpected) {
            messageLabel.setText("Não foi possível salvar. Verifique os campos.");
        }
    }

    @FXML
    public void onCancel() {
        userSession.setProdutoEmEdicaoId(null);
        navigator.navigate(Page.ESTOQUE);
    }

    private void refreshProfit() {
        BigDecimal cost = DecimalInput.parseOrNull(costField.getText());
        BigDecimal price = DecimalInput.parseOrNull(priceField.getText());
        BigDecimal profit = pricingSuggestionService.profitOf(price, cost);
        BigDecimal marginPercent = pricingSuggestionService.marginPercentOf(price, cost);
        if (profit == null || marginPercent == null) {
            profitLabel.setText("-");
            return;
        }
        profitLabel.setText("R$ " + profit.toPlainString() + " (" + marginPercent.toPlainString() + "%)");
    }

    private String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String formatQuantity(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
