package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import br.com.scopel.pfv.beta.hardware.mercadopago.MercadoPagoTerminalModeControl;
import br.com.scopel.pfv.beta.hardware.mercadopago.TerminalMode;
import br.com.scopel.pfv.beta.service.AppSettingService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class ConfigTerminalController {

    private final MercadoPagoTerminalModeControl control;
    private final AppSettingService settings;

    @FXML private Label modeLabel;
    @FXML private Button refreshButton;
    @FXML private Button pdvButton;
    @FXML private Button manualButton;
    @FXML private Label messageLabel;

    public ConfigTerminalController(MercadoPagoTerminalModeControl control, AppSettingService settings) {
        this.control = control;
        this.settings = settings;
    }

    @FXML
    public void initialize() {
        if (!control.isAvailable()) {
            modeLabel.setText("Maquininha não configurada (faltam MP_ACCESS_TOKEN e MP_TERMINAL_ID no .env).");
            setButtonsDisabled(true);
            return;
        }
        onRefresh();
    }

    @FXML
    public void onRefresh() {
        modeLabel.setText("Consultando a maquininha...");
        setButtonsDisabled(true);
        run(control::currentMode, this::showMode);
    }

    @FXML
    public void onUsePdv() {
        switchTo(TerminalMode.PDV);
    }

    @FXML
    public void onUseManual() {
        switchTo(TerminalMode.STANDALONE);
    }

    private void switchTo(TerminalMode mode) {
        messageLabel.setText("Alterando o modo da maquininha...");
        setButtonsDisabled(true);
        run(() -> {
            control.switchTo(mode);
            return mode;
        }, changed -> {
            showMode(changed);
            messageLabel.setText("Modo alterado. Reinicie a maquininha para a mudança valer.");
        });
    }

    private <T> void run(Supplier<T> task, Consumer<T> onSuccess) {
        CompletableFuture.supplyAsync(task).whenComplete((result, failure) -> Platform.runLater(() -> {
            if (failure == null) {
                onSuccess.accept(result);
                return;
            }
            setButtonsDisabled(false);
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause()
                    : failure;
            messageLabel.setText(cause instanceof CardGatewayException
                    ? cause.getMessage()
                    : "Não foi possível falar com a maquininha.");
        }));
    }

    private void showMode(TerminalMode mode) {
        if (mode != TerminalMode.UNKNOWN) {
            settings.set(AppSettingService.TERMINAL_MANUAL_MODE, String.valueOf(mode == TerminalMode.STANDALONE));
        }
        modeLabel.setText(switch (mode) {
            case PDV -> "Modo atual: PDV (o sistema envia o valor para a maquininha)";
            case STANDALONE -> "Modo atual: manual (o valor é digitado na maquininha)";
            case UNKNOWN -> "Modo atual: desconhecido";
        });
        refreshButton.setDisable(false);
        pdvButton.setDisable(mode == TerminalMode.PDV);
        manualButton.setDisable(mode == TerminalMode.STANDALONE);
    }

    private void setButtonsDisabled(boolean disabled) {
        refreshButton.setDisable(disabled);
        pdvButton.setDisable(disabled);
        manualButton.setDisable(disabled);
    }
}
