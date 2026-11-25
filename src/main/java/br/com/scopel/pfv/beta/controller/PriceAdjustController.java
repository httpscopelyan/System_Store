package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.ManagerAuthorizedAction;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PriceAdjustController {

    private final ProductService productService;
    private final UserSession userSession;
    private final ManagerAuthorizationController managerAuthorizationController;

    @FXML private TextField percentField;
    @FXML private TextField categoryField;
    @FXML private Label messageLabel;

    public PriceAdjustController(ProductService productService, UserSession userSession,
                                 ManagerAuthorizationController managerAuthorizationController) {
        this.productService = productService;
        this.userSession = userSession;
        this.managerAuthorizationController = managerAuthorizationController;
    }

    @FXML
    public void onApply() {
        try {
            BigDecimal percent = new BigDecimal(percentField.getText().trim().replace(',', '.'));
            String category = categoryField.getText();
            ManagerAuthorizedAction managerAction =
                    new ManagerAuthorizedAction(managerAuthorizationController, userSession, this::window);
            Integer changed = managerAction.apply(actor -> productService.adjustPricesByPercent(percent, category, actor));
            if (changed != null) {
                messageLabel.setText(changed + " produto(s) reajustado(s).");
            }
        } catch (NumberFormatException e) {
            messageLabel.setText("Percentual inválido");
        } catch (ManagerAuthorizationRequiredException notAllowed) {
            messageLabel.setText("Seu usuário não tem permissão para reajustar preços.");
        } catch (Exception e) {
            messageLabel.setText("Não foi possível aplicar o ajuste");
        }
    }

    private Window window() {
        return messageLabel.getScene() == null ? null : messageLabel.getScene().getWindow();
    }
}
