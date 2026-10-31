package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.service.UpdateService;
import br.com.scopel.pfv.beta.update.UpdateInfo;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class UpdateNotice {

    private static final long EXIT_FALLBACK_MILLIS = 15_000;

    private final UpdateService updateService;
    private final Button updateButton;
    private final BooleanSupplier saleInProgress;
    private final Consumer<String> hint;

    public UpdateNotice(UpdateService updateService, Button updateButton, BooleanSupplier saleInProgress,
                        Consumer<String> hint) {
        this.updateService = updateService;
        this.updateButton = updateButton;
        this.saleInProgress = saleInProgress;
        this.hint = hint;
    }

    public void setup() {
        updateService.addListener(info -> Platform.runLater(() -> showUpdateAvailable(info)));
        UpdateInfo pending = updateService.getLatestUpdate();
        if (pending != null) {
            showUpdateAvailable(pending);
        }
        Platform.runLater(() -> updateService.consumeLastResult()
                .filter(result -> !result.isSuccess())
                .ifPresent(result -> {
                    boolean rolledBack = "ROLLED_BACK".equals(result.status());
                    Alert alert = new Alert(rolledBack ? Alert.AlertType.WARNING : Alert.AlertType.ERROR);
                    alert.setTitle("Atualização");
                    alert.setHeaderText("A atualização para a versão " + result.targetVersion() + " não foi concluída");
                    alert.setContentText((rolledBack
                            ? "A versão anterior foi restaurada e o sistema segue funcionando normalmente.\n\n"
                            : "") + result.message());
                    alert.show();
                }));
    }

    public void updateAndRestart() {
        UpdateInfo info = updateService.getLatestUpdate();
        if (info == null || updateService.isUpdateInProgress()) {
            return;
        }
        if (saleInProgress.getAsBoolean()) {
            hint.accept("Finalize ou cancele a venda antes de atualizar");
            return;
        }
        ButtonType go = new ButtonType("Atualizar e reiniciar", ButtonBar.ButtonData.OK_DONE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "O PDV será fechado e reaberto automaticamente na versão " + info.version() + ".\n"
                        + "Seus dados e configurações são preservados.\n\n"
                        + (info.changelog().isBlank() ? "" : "Novidades: " + info.changelog()),
                go, ButtonType.CANCEL);
        confirm.setTitle("Atualização disponível");
        confirm.setHeaderText("Versão " + updateService.getCurrentVersion() + " → " + info.version());
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != go) {
            return;
        }

        updateButton.setDisable(true);
        updateButton.setText("Baixando e verificando atualização...");
        Thread worker = new Thread(() -> {
            try {
                updateService.applyUpdateAndRestart(this::shutdownApplication);
            } catch (Exception failure) {
                Platform.runLater(() -> reportFailure(info, failure));
            }
        }, "update-apply");
        worker.setDaemon(true);
        worker.start();
    }

    private void reportFailure(UpdateInfo info, Exception failure) {
        showUpdateAvailable(info);
        Alert error = new Alert(Alert.AlertType.ERROR);
        error.setTitle("Atualização");
        error.setHeaderText("Não foi possível atualizar agora");
        error.setContentText(failure.getMessage() + "\n\nO sistema continua na versão atual.");
        error.show();
    }

    private void showUpdateAvailable(UpdateInfo info) {
        updateButton.setText("⬆ Atualizar e reiniciar (v" + info.version() + ")");
        updateButton.setDisable(false);
        updateButton.setVisible(true);
        updateButton.setManaged(true);
    }

    private void shutdownApplication() {
        Platform.runLater(Platform::exit);
        Thread fallback = new Thread(() -> {
            try {
                Thread.sleep(EXIT_FALLBACK_MILLIS);
            } catch (InterruptedException interrupted) {
                return;
            }
            System.exit(0);
        }, "update-exit-fallback");
        fallback.setDaemon(true);
        fallback.start();
    }
}
