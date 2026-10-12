package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.BetaApplication;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.UserService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginController {

    private final UserService userService;
    private final UserSession userSession;

    @FXML private TextField loginField;
    @FXML private PasswordField passField;
    @FXML private Label mensage;

    public LoginController(UserService userService, UserSession userSession) {
        this.userService = userService;
        this.userSession = userSession;
    }

    @FXML
    public void onLogin() {
        try {
            String loginText = loginField.getText();
            String passText = passField.getText();

            User loggedUser = userService.login(loginText, passText);

            userSession.setLoggedUser(loggedUser);

            loginField.clear();
            passField.clear();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/inicio.fxml"));
            loader.setControllerFactory(BetaApplication.springContext::getBean);
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) loginField.getScene().getWindow();
            stage.setScene(scene);

        } catch (UserNotFindException | InvalidPasswordException e) {
            mensage.setText("Login ou senha inválidos!");
        } catch (IOException e) {
            mensage.setText("Erro ao abrir a próxima tela");
        }
    }
}