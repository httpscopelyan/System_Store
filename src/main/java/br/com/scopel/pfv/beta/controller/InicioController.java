package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.stereotype.Component;

/**
 * Página inicial exibida no centro do shell. Mostra só a marca e o status do
 * caixa; barras, relógio e navegação migraram para o MainController.
 */
@Component
public class InicioController {

    private final UserSession userSession;

    @FXML private Label caixaStatusLabel;

    public InicioController(UserSession userSession) {
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        boolean caixaAberto = userSession.getCaixaAtual() != null;
        caixaStatusLabel.setText(caixaAberto ? "Caixa aberto" : "Caixa fechado");
    }
}
