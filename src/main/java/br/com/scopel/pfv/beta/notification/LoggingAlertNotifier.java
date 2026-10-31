package br.com.scopel.pfv.beta.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoggingAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertNotifier.class);

    @Override
    public void send(String subject, String body, List<String> recipients) {
        log.info("[Alertas] (simulado) para {} | {}\n{}", recipients, subject, body);
    }

    @Override
    public boolean canDeliver() {
        return false;
    }
}
