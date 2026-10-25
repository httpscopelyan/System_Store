package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.BetaApplication;
import br.com.scopel.pfv.beta.config.ThemeManager;
import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.UserService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Diálogo modal reutilizável de autorização por senha de supervisor (G1 / RF41).
 * Pede login + senha de um gerente, valida no {@link UserService} (BCrypt) e devolve
 * o gerente que autorizou — SEM trocar o operador logado na sessão.
 *
 * <p>Teclado-first (RNF03): foco inicial no login, Enter confirma, Esc cancela.
 * Erro de credencial aparece inline e NÃO fecha o diálogo (permite nova tentativa).
 */
@Component
public class ManagerAuthorizationController {

    private final UserService userService;
    private final ThemeManager themeManager;

    @FXML private TextField managerLoginField;
    @FXML private PasswordField managerPasswordField;
    @FXML private Label errorLabel;

    private Stage dialogStage;
    private User authorizedManager;

    public ManagerAuthorizationController(UserService userService, ThemeManager themeManager) {
        this.userService = userService;
        this.themeManager = themeManager;
    }

    /**
     * Abre o diálogo modal sobre a janela informada e bloqueia até o desfecho.
     * Retorna o gerente autenticado quando a operação é autorizada, ou vazio
     * quando o usuário cancela (Esc / botão Cancelar).
     */
    public Optional<User> requestAuthorization(Window ownerWindow) {
        authorizedManager = null;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/manager_auth.fxml"));
        loader.setControllerFactory(BetaApplication.springContext::getBean);
        Parent dialogRoot;
        try {
            dialogRoot = loader.load();
        } catch (IOException loadError) {
            throw new RuntimeException("Falha ao abrir o diálogo de autorização de gerente", loadError);
        }

        themeManager.apply(dialogRoot);

        dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        if (ownerWindow != null) {
            dialogStage.initOwner(ownerWindow);
        }
        dialogStage.setTitle("Autorização de gerente");
        dialogStage.setResizable(false);

        Scene dialogScene = new Scene(dialogRoot);
        dialogScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                onCancel();
            }
        });
        dialogStage.setScene(dialogScene);

        Platform.runLater(() -> managerLoginField.requestFocus());
        dialogStage.showAndWait();

        return Optional.ofNullable(authorizedManager);
    }

    @FXML
    public void onConfirm() {
        try {
            String managerLogin = managerLoginField.getText();
            String rawPassword = managerPasswordField.getText();
            authorizedManager = userService.authenticateManager(managerLogin, rawPassword);
            managerPasswordField.clear();
            closeDialog();
        } catch (UserNotFindException notFound) {
            showError("Usuário não encontrado");
        } catch (InvalidPasswordException wrongPassword) {
            showError("Senha incorreta");
        } catch (ManagerAuthorizationRequiredException notManager) {
            showError("Usuário não é gerente");
        }
    }

    @FXML
    public void onCancel() {
        authorizedManager = null;
        closeDialog();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        managerPasswordField.clear();
        managerPasswordField.requestFocus();
    }

    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }
}
