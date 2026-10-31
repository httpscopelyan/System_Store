package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.controller.support.DecimalInput;
import br.com.scopel.pfv.beta.exception.NotificationException;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.alert.AlertRecipients;
import br.com.scopel.pfv.beta.service.alert.StockAlertMailService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Component
public class ConfigAlertsController {

    private static final int MIN_INTERVAL_MINUTES = 15;
    private static final int MAX_PORT = 65535;

    private final AppSettingService settings;
    private final StockAlertMailService mailService;

    @FXML private TextField marginField;
    @FXML private TextField intervalField;
    @FXML private CheckBox emailEnabledBox;
    @FXML private TextField emailToField;
    @FXML private TextField smtpHostField;
    @FXML private TextField smtpPortField;
    @FXML private TextField smtpUserField;
    @FXML private TextField smtpFromField;
    @FXML private Label messageLabel;

    public ConfigAlertsController(AppSettingService settings, StockAlertMailService mailService) {
        this.settings = settings;
        this.mailService = mailService;
    }

    @FXML
    public void initialize() {
        marginField.setText(settings.getString(AppSettingService.STOCK_MIN_ALERT_MARGIN, "5"));
        intervalField.setText(settings.getString(AppSettingService.ALERTS_EMAIL_INTERVAL_MINUTES, "1440"));
        emailEnabledBox.setSelected(settings.getBoolean(AppSettingService.ALERTS_EMAIL_ENABLED, false));
        emailToField.setText(settings.getString(AppSettingService.ALERTS_EMAIL_TO, ""));
        smtpHostField.setText(settings.getString(AppSettingService.ALERTS_SMTP_HOST, ""));
        smtpPortField.setText(settings.getString(AppSettingService.ALERTS_SMTP_PORT, "587"));
        smtpUserField.setText(settings.getString(AppSettingService.ALERTS_SMTP_USER, ""));
        smtpFromField.setText(settings.getString(AppSettingService.ALERTS_SMTP_FROM, ""));
    }

    @FXML
    public void onSave() {
        if (saveValidated()) {
            messageLabel.setText("Avisos de estoque salvos.");
        }
    }

    @FXML
    public void onTest() {
        if (!saveValidated()) {
            return;
        }
        messageLabel.setText("Enviando e-mail de teste...");
        CompletableFuture.runAsync(mailService::sendTest)
                .whenComplete((unused, failure) -> Platform.runLater(() -> finishTest(failure)));
    }

    private void finishTest(Throwable failure) {
        if (failure == null) {
            messageLabel.setText("E-mail de teste enviado.");
            return;
        }
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        messageLabel.setText(cause instanceof NotificationException
                ? cause.getMessage()
                : "Não foi possível enviar o e-mail de teste.");
    }

    private boolean saveValidated() {
        BigDecimal margin = DecimalInput.parseOrNull(marginField.getText());
        if (margin == null || margin.signum() < 0) {
            messageLabel.setText("Margem de aviso inválida: informe um número maior ou igual a zero.");
            return false;
        }
        Integer interval = parseIntOrNull(intervalField.getText());
        if (interval == null || interval < MIN_INTERVAL_MINUTES) {
            messageLabel.setText("Intervalo inválido: mínimo de " + MIN_INTERVAL_MINUTES + " minutos.");
            return false;
        }
        Integer port = parseIntOrNull(smtpPortField.getText());
        if (port == null || port < 1 || port > MAX_PORT) {
            messageLabel.setText("Porta SMTP inválida.");
            return false;
        }
        List<String> recipients;
        try {
            recipients = AlertRecipients.parse(emailToField.getText());
        } catch (IllegalArgumentException invalidRecipient) {
            messageLabel.setText(invalidRecipient.getMessage());
            return false;
        }
        if (emailEnabledBox.isSelected() && recipients.isEmpty()) {
            messageLabel.setText("Informe ao menos um e-mail para receber os avisos.");
            return false;
        }
        settings.set(AppSettingService.STOCK_MIN_ALERT_MARGIN, margin.stripTrailingZeros().toPlainString());
        settings.set(AppSettingService.ALERTS_EMAIL_INTERVAL_MINUTES, String.valueOf(interval));
        settings.set(AppSettingService.ALERTS_EMAIL_ENABLED, String.valueOf(emailEnabledBox.isSelected()));
        settings.set(AppSettingService.ALERTS_EMAIL_TO, String.join(", ", recipients));
        settings.set(AppSettingService.ALERTS_SMTP_HOST, smtpHostField.getText().trim());
        settings.set(AppSettingService.ALERTS_SMTP_PORT, String.valueOf(port));
        settings.set(AppSettingService.ALERTS_SMTP_USER, smtpUserField.getText().trim());
        settings.set(AppSettingService.ALERTS_SMTP_FROM, smtpFromField.getText().trim());
        return true;
    }

    private Integer parseIntOrNull(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException invalidNumber) {
            return null;
        }
    }
}
