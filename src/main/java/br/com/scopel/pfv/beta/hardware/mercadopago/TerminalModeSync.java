package br.com.scopel.pfv.beta.hardware.mercadopago;

import br.com.scopel.pfv.beta.service.AppSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class TerminalModeSync implements ApplicationRunner {

    private final MercadoPagoTerminalModeControl control;
    private final AppSettingService settings;

    public TerminalModeSync(MercadoPagoTerminalModeControl control, AppSettingService settings) {
        this.control = control;
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!control.isAvailable()) {
            return;
        }
        CompletableFuture.runAsync(this::sync);
    }

    void sync() {
        try {
            TerminalMode mode = control.currentMode();
            if (mode != TerminalMode.UNKNOWN) {
                settings.set(AppSettingService.TERMINAL_MANUAL_MODE, String.valueOf(mode == TerminalMode.STANDALONE));
            }
        } catch (RuntimeException unreachable) {
            log.warn("[Maquininha] Não foi possível ler o modo da maquininha: {}", unreachable.getMessage());
        }
    }
}
