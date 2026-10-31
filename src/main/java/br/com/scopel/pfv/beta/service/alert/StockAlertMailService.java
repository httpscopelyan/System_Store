package br.com.scopel.pfv.beta.service.alert;

import br.com.scopel.pfv.beta.exception.NotificationException;
import br.com.scopel.pfv.beta.notification.AlertNotifier;
import br.com.scopel.pfv.beta.service.AppSettingService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;

@Service
public class StockAlertMailService {

    private static final int DEFAULT_INTERVAL_MINUTES = 1440;
    private static final int MIN_INTERVAL_MINUTES = 15;
    private static final String DEFAULT_STORE_NAME = "Mercado";

    private final StockAlertService stockAlertService;
    private final StockAlertFormatter formatter;
    private final AlertNotifier notifier;
    private final AppSettingService appSettingService;

    public StockAlertMailService(StockAlertService stockAlertService, StockAlertFormatter formatter,
                                 AlertNotifier notifier, AppSettingService appSettingService) {
        this.stockAlertService = stockAlertService;
        this.formatter = formatter;
        this.notifier = notifier;
        this.appSettingService = appSettingService;
    }

    public int intervalMinutes() {
        int configured = appSettingService.getInt(
                AppSettingService.ALERTS_EMAIL_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES);
        return Math.max(MIN_INTERVAL_MINUTES, configured);
    }

    public boolean hasRecipients() {
        try {
            return !AlertRecipients.parse(
                    appSettingService.getString(AppSettingService.ALERTS_EMAIL_TO, "")).isEmpty();
        } catch (IllegalArgumentException invalidRecipient) {
            return false;
        }
    }

    public int sendNow() {
        List<String> recipients = requireDeliverableRecipients();
        List<StockAlert> alerts = stockAlertService.lowStockItems();
        if (alerts.isEmpty()) {
            return 0;
        }
        String body = formatter.body(storeName(), alerts);
        notifier.send(formatter.subject(storeName(), alerts.size()), body, recipients);
        rememberSent(body, LocalDateTime.now());
        return alerts.size();
    }

    public void sendTest() {
        List<String> recipients = requireDeliverableRecipients();
        notifier.send(storeName() + " — teste de aviso de estoque",
                "Este é um e-mail de teste dos avisos de estoque mínimo.\n", recipients);
    }

    public boolean sendScheduledIfDue(LocalDateTime now) {
        if (!appSettingService.getBoolean(AppSettingService.ALERTS_EMAIL_ENABLED, false)) {
            return false;
        }
        List<String> recipients = AlertRecipients.parse(
                appSettingService.getString(AppSettingService.ALERTS_EMAIL_TO, ""));
        if (recipients.isEmpty() || !notifier.canDeliver()) {
            return false;
        }
        List<StockAlert> alerts = stockAlertService.lowStockItems();
        if (alerts.isEmpty()) {
            return false;
        }
        String body = formatter.body(storeName(), alerts);
        if (isSameAsRecentlySent(body, now)) {
            return false;
        }
        notifier.send(formatter.subject(storeName(), alerts.size()), body, recipients);
        rememberSent(body, now);
        return true;
    }

    private List<String> requireDeliverableRecipients() {
        List<String> recipients = AlertRecipients.parse(
                appSettingService.getString(AppSettingService.ALERTS_EMAIL_TO, ""));
        if (recipients.isEmpty()) {
            throw new NotificationException("Nenhum e-mail de aviso definido nas configurações");
        }
        if (!notifier.canDeliver()) {
            throw new NotificationException(
                    "SMTP não configurado: informe servidor e remetente nas configurações e SMTP_PASSWORD no .env");
        }
        return recipients;
    }

    private boolean isSameAsRecentlySent(String body, LocalDateTime now) {
        String lastDigest = appSettingService.getString(AppSettingService.ALERTS_LAST_SENT_DIGEST, "");
        if (!digestOf(body).equals(lastDigest)) {
            return false;
        }
        LocalDateTime lastSentAt = lastSentAt();
        return lastSentAt != null && Duration.between(lastSentAt, now).toMinutes() < intervalMinutes();
    }

    private LocalDateTime lastSentAt() {
        String stored = appSettingService.getString(AppSettingService.ALERTS_LAST_SENT_AT, "");
        if (stored.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(stored);
        } catch (DateTimeParseException invalidTimestamp) {
            return null;
        }
    }

    private void rememberSent(String body, LocalDateTime sentAt) {
        appSettingService.set(AppSettingService.ALERTS_LAST_SENT_DIGEST, digestOf(body));
        appSettingService.set(AppSettingService.ALERTS_LAST_SENT_AT, sentAt.toString());
    }

    private String storeName() {
        return appSettingService.getString(AppSettingService.STORE_NAME, DEFAULT_STORE_NAME);
    }

    private String digestOf(String body) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException missingAlgorithm) {
            throw new IllegalStateException(missingAlgorithm);
        }
    }
}
