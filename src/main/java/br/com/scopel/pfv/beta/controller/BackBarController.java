package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.controller.support.EscapeToBack;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import org.springframework.stereotype.Component;

@Component
public class BackBarController {

    private final Navigator navigator;

    @FXML private Button backButton;

    public BackBarController(Navigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    public void initialize() {
        EscapeToBack.install(backButton, navigator);
    }

    @FXML
    public void onBack() {
        navigator.back();
    }
}
