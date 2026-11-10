package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.controller.support.SessionLauncher;
import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.UserService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginController {

    private final UserService userService;
    private final SessionLauncher sessionLauncher;

    @FXML private TextField loginField;
    @FXML private PasswordField passField;
    @FXML private Label mensage;

    public LoginController(UserService userService, SessionLauncher sessionLauncher) {
        this.userService = userService;
        this.sessionLauncher = sessionLauncher;
    }

    @FXML
    public void initialize() {
        // Foco no campo de login ao abrir a tela.
        Platform.runLater(() -> loginField.requestFocus());
    }

    @FXML
    public void onLogin() {
        try {
            String loginText = loginField.getText();
            String passText = passField.getText();

            User loggedUser = userService.login(loginText, passText);

            loginField.clear();
            passField.clear();

            sessionLauncher.start(loggedUser, loginField.getScene());

        } catch (UserNotFindException | InvalidPasswordException e) {
            mensage.setText("Login ou senha inválidos!");
        } catch (IOException e) {
            mensage.setText("Erro ao abrir a próxima tela");
        }
    }
}