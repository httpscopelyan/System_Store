package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.SessionLauncher;
import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.AccountSummary;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.UserService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.FlowPane;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SwitchAccountController {

    private static final String SALE_IN_PROGRESS_MESSAGE =
            "Há uma venda em andamento. Finalize ou cancele a venda antes de trocar de conta.";

    private final UserService userService;
    private final UserSession userSession;
    private final SessionLauncher sessionLauncher;

    private AccountSummary selectedAccount;

    @FXML private Node accountsPanel;
    @FXML private FlowPane accountCardsPane;
    @FXML private Node passwordPanel;
    @FXML private Label selectedAccountLabel;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    public SwitchAccountController(UserService userService, UserSession userSession,
                                   SessionLauncher sessionLauncher) {
        this.userService = userService;
        this.userSession = userSession;
        this.sessionLauncher = sessionLauncher;
    }

    @FXML
    public void initialize() {
        selectedAccount = null;
        messageLabel.setText("");
        if (hasSaleInProgress()) {
            showPanels(false, false);
            messageLabel.setText(SALE_IN_PROGRESS_MESSAGE);
            return;
        }
        accountCardsPane.getChildren().clear();
        for (AccountSummary account : userService.listActiveAccounts()) {
            accountCardsPane.getChildren().add(cardOf(account));
        }
        showPanels(true, false);
    }

    @FXML
    public void onChooseAnotherAccount() {
        selectedAccount = null;
        passwordField.clear();
        messageLabel.setText("");
        showPanels(true, false);
    }

    @FXML
    public void onEnter() {
        if (selectedAccount == null || hasSaleInProgress()) {
            return;
        }
        try {
            User operator = userService.login(selectedAccount.login(), passwordField.getText());
            sessionLauncher.start(operator, passwordPanel.getScene());
        } catch (UserNotFindException | InvalidPasswordException wrongCredentials) {
            passwordField.clear();
            messageLabel.setText("Senha incorreta para a conta " + selectedAccount.name() + ".");
        } catch (IOException shellFailure) {
            messageLabel.setText("Erro ao abrir o sistema com a nova conta.");
        }
    }

    private Button cardOf(AccountSummary account) {
        Button card = new Button(account.name() + "\n" + account.role());
        card.getStyleClass().add("account-card");
        card.setOnAction(event -> selectAccount(account));
        return card;
    }

    private void selectAccount(AccountSummary account) {
        selectedAccount = account;
        selectedAccountLabel.setText(account.name());
        passwordField.clear();
        messageLabel.setText("");
        showPanels(false, true);
        Platform.runLater(passwordField::requestFocus);
    }

    private boolean hasSaleInProgress() {
        Sale sale = userSession.getVendaAtual();
        return sale != null && ((sale.getItems() != null && !sale.getItems().isEmpty())
                || sale.getStatus() == Sale.Status.EM_PAGAMENTO);
    }

    private void showPanels(boolean accounts, boolean password) {
        accountsPanel.setVisible(accounts);
        accountsPanel.setManaged(accounts);
        passwordPanel.setVisible(password);
        passwordPanel.setManaged(password);
    }
}
