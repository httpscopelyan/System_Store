package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
@Slf4j
public class AdministratorYanSeed implements ApplicationRunner {

    static final String LOGIN = "Yan";
    static final String PASSWORD_HASH = "$2a$10$KD/P/4b/RnbNlHdtp92nd.ef2PN24nRGKc4PEbJh2jHWLQu3.bEHu";
    static final String DONE_FLAG = "seed.administrator_yan.done";

    private final UserService userService;
    private final AppSettingService settings;

    public AdministratorYanSeed(UserService userService, AppSettingService settings) {
        this.userService = userService;
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.getBoolean(DONE_FLAG, false) || !userService.hasUsers()) {
            return;
        }
        if (userService.registerWithPasswordHash(LOGIN, LOGIN, PASSWORD_HASH, User.Role.DEV)) {
            log.info("[AdministratorYanSeed] Administrador '{}' criado.", LOGIN);
        } else {
            log.warn("[AdministratorYanSeed] Já existe um usuário '{}'; a conta não foi alterada.", LOGIN);
        }
        settings.set(DONE_FLAG, "true");
    }
}
