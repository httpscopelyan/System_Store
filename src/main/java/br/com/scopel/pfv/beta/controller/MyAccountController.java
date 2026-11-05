package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.UserDuplicatedException;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.UserService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

@Component
public class MyAccountController {

    private final UserService userService;
    private final UserSession userSession;

    @FXML private TextField nameField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private PasswordField currentPasswordField;
    @FXML private Label messageLabel;

    public MyAccountController(UserService userService, UserSession userSession) {
        this.userService = userService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        User user = userSession.getLoggedUser();
        nameField.setText(user != null ? user.getLogin() : "");
    }

    @FXML
    public void onSave() {
        User user = userSession.getLoggedUser();
        if (user == null) {
            return;
        }
        if (!newPasswordField.getText().equals(confirmPasswordField.getText())) {
            messageLabel.setText("A confirmação não confere com a senha nova.");
            return;
        }
        try {
            User saved = userService.updateOwnCredentials(user.getId(), currentPasswordField.getText(),
                    nameField.getText(), newPasswordField.getText());
            userSession.setLoggedUser(saved);
            nameField.setText(saved.getLogin());
            clearPasswords();
            messageLabel.setText("Dados salvos. Na próxima entrada use o nome \"" + saved.getLogin() + "\".");
        } catch (InvalidPasswordException wrongPassword) {
            messageLabel.setText("Senha atual incorreta.");
        } catch (UserDuplicatedException duplicated) {
            messageLabel.setText("Já existe um usuário com esse nome.");
        } catch (IllegalArgumentException invalid) {
            messageLabel.setText(invalid.getMessage());
        }
    }

    private void clearPasswords() {
        newPasswordField.clear();
        confirmPasswordField.clear();
        currentPasswordField.clear();
    }
}
