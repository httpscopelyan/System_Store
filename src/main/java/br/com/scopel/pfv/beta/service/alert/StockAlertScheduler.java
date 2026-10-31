package br.com.scopel.pfv.beta.service.alert;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class StockAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(StockAlertScheduler.class);
    private static final long FIRST_RUN_DELAY_SECONDS = 60;

    private final StockAlertMailService mailService;

    private ScheduledExecutorService executor;

    public StockAlertScheduler(StockAlertMailService mailService) {
        this.mailService = mailService;
    }

    @PostConstruct
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "stock-alert-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.schedule(this::tick, FIRST_RUN_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public void runOnce() {
        try {
            if (mailService.sendScheduledIfDue(LocalDateTime.now())) {
                log.info("[Alertas] Aviso de estoque mínimo enviado por e-mail.");
            }
        } catch (Exception failure) {
            log.warn("[Alertas] Não foi possível enviar o aviso de estoque: {}", failure.getMessage());
        }
    }

    private void tick() {
        runOnce();
        if (!executor.isShutdown()) {
            executor.schedule(this::tick, nextDelayMinutes(), TimeUnit.MINUTES);
        }
    }

    private long nextDelayMinutes() {
        try {
            return mailService.intervalMinutes();
        } catch (Exception failure) {
            return 60;
        }
    }
}
