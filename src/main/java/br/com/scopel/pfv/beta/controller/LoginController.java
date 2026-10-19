package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.BetaApplication;
import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.ThemeManager;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.UserService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginController {

    private final UserService userService;
    private final UserSession userSession;
    private final CashRegisterService cashRegisterService;
    private final Navigator navigator;
    private final ThemeManager themeManager;

    @FXML private TextField loginField;
    @FXML private PasswordField passField;
    @FXML private Label mensage;

    public LoginController(UserService userService, UserSession userSession,
                           CashRegisterService cashRegisterService, Navigator navigator,
                           ThemeManager themeManager) {
        this.userService = userService;
        this.userSession = userSession;
        this.cashRegisterService = cashRegisterService;
        this.navigator = navigator;
        this.themeManager = themeManager;
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

            userSession.setLoggedUser(loggedUser);
            userSession.setCaixaAtual(cashRegisterService.findOpenByOperator(loggedUser).orElse(null));

            loginField.clear();
            passField.clear();

            // Carrega a casca persistente e troca apenas a raiz da Scene atual,
            // preservando o tamanho da janela (sem stage.setScene).
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_layout.fxml"));
            loader.setControllerFactory(BetaApplication.springContext::getBean);
            Parent shellRoot = loader.load();
            themeManager.apply(shellRoot);
            Scene scene = loginField.getScene();
            scene.setRoot(shellRoot);

            navigator.navigate(Page.INICIO);

        } catch (UserNotFindException | InvalidPasswordException e) {
            mensage.setText("Login ou senha inválidos!");
        } catch (IOException e) {
            mensage.setText("Erro ao abrir a próxima tela");
        }
    }
}