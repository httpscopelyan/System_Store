package br.com.scopel.pfv.beta.notification;

import br.com.scopel.pfv.beta.exception.NotificationException;
import br.com.scopel.pfv.beta.service.AppSettingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;

@Component
public class SmtpAlertNotifier implements AlertNotifier {

    private static final int SMTP_SSL_PORT = 465;
    private static final int SMTP_DEFAULT_PORT = 587;
    private static final String TIMEOUT_MILLIS = "10000";

    private final AppSettingService appSettingService;
    private final String smtpPassword;

    public SmtpAlertNotifier(AppSettingService appSettingService,
                             @Value("${SMTP_PASSWORD:}") String smtpPassword) {
        this.appSettingService = appSettingService;
        this.smtpPassword = smtpPassword;
    }

    @Override
    public boolean canDeliver() {
        return !host().isBlank() && !from().isBlank() && !smtpPassword.isBlank();
    }

    @Override
    public void send(String subject, String body, List<String> recipients) {
        if (!canDeliver()) {
            throw new NotificationException(
                    "SMTP não configurado: informe servidor e remetente nas configurações e SMTP_PASSWORD no .env");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from());
        message.setTo(recipients.toArray(new String[0]));
        message.setSubject(subject);
        message.setText(body);
        try {
            buildSender().send(message);
        } catch (RuntimeException deliveryFailure) {
            throw new NotificationException("Falha ao enviar o e-mail: " + deliveryFailure.getMessage(),
                    deliveryFailure);
        }
    }

    private JavaMailSenderImpl buildSender() {
        int port = appSettingService.getInt(AppSettingService.ALERTS_SMTP_PORT, SMTP_DEFAULT_PORT);
        String user = appSettingService.getString(AppSettingService.ALERTS_SMTP_USER, "");
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host());
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.connectiontimeout", TIMEOUT_MILLIS);
        properties.put("mail.smtp.timeout", TIMEOUT_MILLIS);
        properties.put("mail.smtp.writetimeout", TIMEOUT_MILLIS);
        if (!user.isBlank()) {
            sender.setUsername(user);
            sender.setPassword(smtpPassword);
            properties.put("mail.smtp.auth", "true");
        }
        if (port == SMTP_SSL_PORT) {
            properties.put("mail.smtp.ssl.enable", "true");
        } else {
            properties.put("mail.smtp.starttls.enable", "true");
        }
        return sender;
    }

    private String host() {
        return appSettingService.getString(AppSettingService.ALERTS_SMTP_HOST, "").trim();
    }

    private String from() {
        return appSettingService.getString(AppSettingService.ALERTS_SMTP_FROM, "").trim();
    }
}
