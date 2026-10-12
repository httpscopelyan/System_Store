package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.BetaApplication;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.User;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class InicioController {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy  HH:mm:ss", PT_BR);

    private final UserSession userSession;

    @FXML private BorderPane root;
    @FXML private VBox usersGroup;
    @FXML private Label caixaStatusLabel;
    @FXML private Label dateTimeLabel;
    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private Label terminalLabel;

    public InicioController(UserSession userSession) {
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        User user = userSession.getLoggedUser();

        userLabel.setText("Usuário: " + (user != null ? user.getName() : "-"));
        roleLabel.setText("Grupo: " + (user != null ? user.getRole() : "-"));

        boolean caixaAberto = userSession.getCaixaAtual() != null;
        caixaStatusLabel.setText(caixaAberto ? "Caixa aberto" : "Caixa fechado");

        // Usuários só para GERENTE: oculta (e tira do layout) para OPERADOR
        boolean gerente = user != null && user.getRole() == User.Role.GERENTE;
        usersGroup.setVisible(gerente);
        usersGroup.setManaged(gerente);

        startClock();
        setupShortcuts();
    }

    private void startClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.ZERO,
                e -> dateTimeLabel.setText(LocalDateTime.now().format(DATE_TIME))),
                new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    private void setupShortcuts() {
        // A scene ainda não existe no initialize(); registra os atalhos quando ela chega.
        root.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F2), this::onVender);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F3), this::onCaixa);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F4), this::onProdutos);
        });
    }

    @FXML
    public void onVender() {
        // Só roteia: com caixa aberto vai para a venda, senão abre o caixa primeiro.
        if (userSession.getCaixaAtual() != null) {
            navigate("/fxml/sale.fxml");
        } else {
            navigate("/fxml/caixa.fxml");
        }
    }

    @FXML
    public void onCaixa() {
        navigate("/fxml/caixa.fxml");
    }

    @FXML
    public void onProdutos() {
        navigate("/fxml/create_product.fxml");
    }

    private void navigate(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(BetaApplication.springContext::getBean);
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) root.getScene().getWindow();
            stage.setScene(scene);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
