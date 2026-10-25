package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.BetaApplication;
import br.com.scopel.pfv.beta.config.ThemeManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Tela de início (entrada do app): botões PDV e Configuração.
 * PDV → tela de login → sistema. Configuração ainda não tem conteúdo.
 */
@Component
public class HomeController {

    private final ThemeManager themeManager;

    @FXML private Label messageLabel;

    public HomeController(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    @FXML
    public void onPdv() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            loader.setControllerFactory(BetaApplication.springContext::getBean);
            Parent loginRoot = loader.load();
            themeManager.apply(loginRoot);
            Scene scene = messageLabel.getScene();
            scene.setRoot(loginRoot);
        } catch (IOException e) {
            messageLabel.setText("Erro ao abrir o login");
        }
    }

    @FXML
    public void onConfig() {
        messageLabel.setText("Configurações estarão disponíveis em breve.");
    }
}
