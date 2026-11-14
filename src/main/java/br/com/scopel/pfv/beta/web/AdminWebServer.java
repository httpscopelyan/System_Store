package br.com.scopel.pfv.beta.web;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AdminWebServer {

    public record Status(boolean running, boolean enabled, boolean https, String bind, int port) {
    }

    private final AdminWebProperties properties;
    private final AdminWebRouter router;
    private final AdminWebAuthService authService;

    private AdminHttpListener listener;

    public AdminWebServer(AdminWebProperties properties, AdminWebRouter router, AdminWebAuthService authService) {
        this.properties = properties;
        this.router = router;
        this.authService = authService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.enabled() || listener != null) {
            return;
        }
        try {
            AdminHttpListener created = new AdminHttpListener(properties, router, authService);
            created.start();
            listener = created.running() ? created : null;
        } catch (Throwable unavailable) {
            listener = null;
            log.error("[AdminWeb] Painel web indisponível neste computador: {}", unavailable.toString());
        }
    }

    @PreDestroy
    public void stop() {
        if (listener != null) {
            listener.stop();
            listener = null;
        }
    }

    public Status status() {
        return new Status(listener != null, properties.enabled(), properties.httpsConfigured(), properties.bind(),
                properties.port());
    }
}
