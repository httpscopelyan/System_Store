package br.com.scopel.pfv.beta.notification;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
public class AlertNotifierSelector implements AlertNotifier {

    private final SmtpAlertNotifier smtpNotifier;
    private final LoggingAlertNotifier loggingNotifier;

    public AlertNotifierSelector(SmtpAlertNotifier smtpNotifier, LoggingAlertNotifier loggingNotifier) {
        this.smtpNotifier = smtpNotifier;
        this.loggingNotifier = loggingNotifier;
    }

    @Override
    public void send(String subject, String body, List<String> recipients) {
        current().send(subject, body, recipients);
    }

    @Override
    public boolean canDeliver() {
        return current().canDeliver();
    }

    private AlertNotifier current() {
        return smtpNotifier.canDeliver() ? smtpNotifier : loggingNotifier;
    }
}
