package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.controller.support.EnterToNextField;
import br.com.scopel.pfv.beta.controller.support.ProductCodeInput;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.DecimalInput;
import br.com.scopel.pfv.beta.exception.MissingReasonException;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.StockService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StockAdjustController {

    private static final String ADJUSTMENT = "Ajuste";
    private static final String RECTIFICATION = "Retificação de erro";

    private final StockService stockService;
    private final UserSession userSession;

    private final ProductService productService;
    @FXML private ComboBox<String> kindBox;
    @FXML private TextField codeField;
    @FXML private TextField quantityField;
    @FXML private TextField reasonField;
    @FXML private Label reasonLabel;
    @FXML private Button saveButton;
    @FXML private Label messageLabel;

    public StockAdjustController(StockService stockService, UserSession userSession,
                                 ProductService productService) {
        this.productService = productService;
        this.stockService = stockService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        ProductCodeInput.attach(codeField, productService);
        EnterToNextField.install(kindBox, codeField, quantityField, reasonField);
        kindBox.setItems(FXCollections.observableArrayList(ADJUSTMENT, RECTIFICATION));
        kindBox.setValue(ADJUSTMENT);
        kindBox.valueProperty().addListener((observable, previous, current) -> refreshKind());
        Platform.runLater(() -> codeField.requestFocus());
    }

    @FXML
    public void onSave() {
        try {
            BigDecimal quantity = DecimalInput.parseRequired(quantityField.getText());
            String code = productService.resolveCode(codeField.getText());
            String reason = reasonField.getText();
            Product product = RECTIFICATION.equals(kindBox.getValue())
                    ? stockService.rectify(code, quantity, reason, userSession.getLoggedUser())
                    : stockService.adjust(code, quantity, reason, userSession.getLoggedUser());
            messageLabel.setText((RECTIFICATION.equals(kindBox.getValue()) ? "Estoque retificado para "
                    : "Estoque ajustado para ") + product.getQuantity().toPlainString());
            codeField.clear();
            quantityField.clear();
            reasonField.clear();
        } catch (NumberFormatException invalidNumber) {
            messageLabel.setText("Quantidade inválida");
        } catch (ProductNotfindException notFound) {
            messageLabel.setText("Produto não encontrado");
        } catch (MissingReasonException missingReason) {
            messageLabel.setText(missingReason.getMessage());
        } catch (IllegalArgumentException invalidValue) {
            messageLabel.setText(invalidValue.getMessage());
        } catch (Exception unexpected) {
            messageLabel.setText("Verifique os campos e tente novamente");
        }
    }

    private void refreshKind() {
        boolean rectification = RECTIFICATION.equals(kindBox.getValue());
        saveButton.setText(rectification ? "Retificar estoque" : "Ajustar estoque");
        reasonField.setPromptText(rectification ? "Ex: Quantidade lançada errada na entrada"
                : "Ex: Contagem de inventário");
    }
}
