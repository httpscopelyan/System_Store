package br.com.scopel.pfv.beta.service.cash;

import br.com.scopel.pfv.beta.service.AppSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@Order(4)
@Slf4j
public class DayanaCashReopeningSeed implements ApplicationRunner {

    static final String LOGIN = "Dayana";
    static final String REASON = "Reabertura do último caixa fechado com erro (versão 1.2.21)";
    static final String DONE_FLAG = "seed.reopen_dayana_last_cash.done";
    static final Path BACKUP_DIRECTORY = Path.of("backups");

    private final CashReopeningService reopeningService;
    private final AppSettingService settings;

    public DayanaCashReopeningSeed(CashReopeningService reopeningService, AppSettingService settings) {
        this.reopeningService = reopeningService;
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.getBoolean(DONE_FLAG, false) || !reopeningService.operatorExists(LOGIN)) {
            return;
        }
        reopeningService.reopenLastClosed(LOGIN, REASON, BACKUP_DIRECTORY).ifPresentOrElse(
                register -> log.info("[DayanaCashReopeningSeed] Caixa #{} reaberto; backup em '{}'.",
                        register.getId(), BACKUP_DIRECTORY.toAbsolutePath()),
                () -> log.warn("[DayanaCashReopeningSeed] Nada reaberto: sem caixa fechado ou já há caixa aberto."));
        settings.set(DONE_FLAG, "true");
    }
}
