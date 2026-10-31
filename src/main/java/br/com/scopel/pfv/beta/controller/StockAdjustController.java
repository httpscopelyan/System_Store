package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.DecimalInput;
import br.com.scopel.pfv.beta.controller.support.ManagerAuthorizedAction;
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
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StockAdjustController {

    private static final String ADJUSTMENT = "Ajuste";
    private static final String RECTIFICATION = "Retificação de erro";

    private final StockService stockService;
    private final UserSession userSession;
    private final ManagerAuthorizationController managerAuthorizationController;

    @FXML private ComboBox<String> kindBox;
    @FXML private TextField codeField;
    @FXML private TextField quantityField;
    @FXML private TextField reasonField;
    @FXML private Label reasonLabel;
    @FXML private Button saveButton;
    @FXML private Label messageLabel;

    public StockAdjustController(StockService stockService, UserSession userSession,
                                 ManagerAuthorizationController managerAuthorizationController) {
        this.stockService = stockService;
        this.userSession = userSession;
        this.managerAuthorizationController = managerAuthorizationController;
    }

    @FXML
    public void initialize() {
        kindBox.setItems(FXCollections.observableArrayList(ADJUSTMENT, RECTIFICATION));
        kindBox.setValue(ADJUSTMENT);
        kindBox.valueProperty().addListener((observable, previous, current) -> refreshKind());
        Platform.runLater(() -> codeField.requestFocus());
    }

    @FXML
    public void onSave() {
        try {
            BigDecimal quantity = DecimalInput.parseRequired(quantityField.getText());
            String code = codeField.getText().trim();
            String reason = reasonField.getText();
            Product product = RECTIFICATION.equals(kindBox.getValue())
                    ? rectify(code, quantity, reason)
                    : stockService.adjust(code, quantity, reason, userSession.getLoggedUser());
            if (product == null) {
                messageLabel.setText("Retificação não autorizada.");
                return;
            }
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

    private Product rectify(String code, BigDecimal quantity, String reason) {
        ManagerAuthorizedAction managerAction =
                new ManagerAuthorizedAction(managerAuthorizationController, userSession, this::window);
        return managerAction.apply(actor -> stockService.rectify(code, quantity, reason, actor));
    }

    private void refreshKind() {
        boolean rectification = RECTIFICATION.equals(kindBox.getValue());
        saveButton.setText(rectification ? "Retificar estoque" : "Ajustar estoque");
        reasonField.setPromptText(rectification ? "Ex: Quantidade lançada errada na entrada"
                : "Ex: Contagem de inventário");
    }

    private Window window() {
        return messageLabel.getScene() == null ? null : messageLabel.getScene().getWindow();
    }
}
