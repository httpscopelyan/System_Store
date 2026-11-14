package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.EnterToNextField;
import br.com.scopel.pfv.beta.service.StaffPermissions;
import br.com.scopel.pfv.beta.controller.support.DecimalInput;
import br.com.scopel.pfv.beta.controller.support.SuggestedPriceBinding;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.PricingSuggestionService;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Component
public class CreateProductsController {

    private final ProductService productService;
    private final UserSession userSession;
    private final PricingSuggestionService pricingSuggestionService;

    private SuggestedPriceBinding priceBinding;

    @FXML private TextField codeField;
    @FXML private TextField descriptionField;
    @FXML private TextField costField;
    @FXML private TextField marginField;
    @FXML private TextField priceField;
    @FXML private TextField quantityField;
    @FXML private TextField minStockField;
    @FXML private TextField categoryField;
    @FXML private ComboBox<Product.Unidade> unitBox;
    @FXML private CheckBox activeBox;
    @FXML private Label messageLabel;
    @FXML private Label priceFormationLabel;
    @FXML private Node costBox;
    @FXML private Node marginBox;
    @FXML private Node minStockBox;

    public CreateProductsController(ProductService productService,
                                    PricingSuggestionService pricingSuggestionService,
                                    UserSession userSession) {
        this.userSession = userSession;
        this.productService = productService;
        this.pricingSuggestionService = pricingSuggestionService;
    }

    @FXML
    public void initialize() {
        boolean seesCostFields = StaffPermissions.canSeeCostFields(userSession.getLoggedUser());
        Stream.of(costBox, marginBox, minStockBox, priceFormationLabel).forEach(node -> {
            node.setVisible(seesCostFields);
            node.setManaged(seesCostFields);
        });
        List<Node> enterOrder = new ArrayList<>(List.of(codeField, descriptionField));
        if (seesCostFields) {
            enterOrder.addAll(List.of(costField, marginField));
        }
        enterOrder.addAll(List.of(priceField, quantityField));
        if (seesCostFields) {
            enterOrder.add(minStockField);
        }
        enterOrder.addAll(List.of(unitBox, categoryField));
        EnterToNextField.install(this::onSave, enterOrder.toArray(Node[]::new));
        unitBox.getItems().setAll(Product.Unidade.values());
        unitBox.setValue(Product.Unidade.UN);
        activeBox.setSelected(true);
        marginField.setPromptText("Padrão: "
                + pricingSuggestionService.defaultMarginPercent().stripTrailingZeros().toPlainString());
        priceBinding = new SuggestedPriceBinding(costField, marginField, priceField, pricingSuggestionService, true);
        Platform.runLater(() -> codeField.requestFocus());
    }

    @FXML
    public void onSave() {
        if (!StaffPermissions.canRegisterProducts(userSession.getLoggedUser())) {
            messageLabel.setText("Seu usuário não tem permissão para cadastrar produtos.");
            return;
        }
        try {
            productService.create(
                    codeField.getText(),
                    descriptionField.getText(),
                    unitBox.getValue(),
                    DecimalInput.parseRequired(priceField.getText()),
                    DecimalInput.parseOrZero(costField.getText()),
                    DecimalInput.parseOrZero(quantityField.getText()),
                    DecimalInput.parseOrZero(minStockField.getText()),
                    categoryField.getText(),
                    activeBox.isSelected(),
                    userSession.getLoggedUser());
            clearForm();
            messageLabel.setText("Produto cadastrado!");
            codeField.requestFocus();
        } catch (NumberFormatException invalidNumber) {
            messageLabel.setText("Preço de venda inválido");
        } catch (RegisterProductDuplicateException duplicate) {
            messageLabel.setText("Este código já está cadastrado");
        } catch (ManagerAuthorizationRequiredException notAllowed) {
            messageLabel.setText("Seu usuário não tem permissão para cadastrar produtos.");
        } catch (IllegalArgumentException invalidField) {
            messageLabel.setText(invalidField.getMessage());
        } catch (Exception unexpected) {
            messageLabel.setText("Verifique os campos e tente novamente");
        }
    }

    private void clearForm() {
        codeField.clear();
        descriptionField.clear();
        costField.clear();
        marginField.clear();
        quantityField.clear();
        minStockField.clear();
        categoryField.clear();
        unitBox.setValue(Product.Unidade.UN);
        activeBox.setSelected(true);
        priceBinding.reset();
    }
}
