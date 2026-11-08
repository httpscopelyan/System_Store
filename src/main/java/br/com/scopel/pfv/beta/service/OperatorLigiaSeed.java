package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
@Slf4j
public class OperatorLigiaSeed implements ApplicationRunner {

    static final String LOGIN = "Ligia";
    static final String PASSWORD_HASH = "$2a$10$f97UcuxUlWinhQt2FGsdh.ianzYkMOHkXb8AFKyCgEozeSy0j7Dm6";
    static final String DONE_FLAG = "seed.operator_ligia.done";

    private final UserService userService;
    private final AppSettingService settings;

    public OperatorLigiaSeed(UserService userService, AppSettingService settings) {
        this.userService = userService;
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.getBoolean(DONE_FLAG, false) || !userService.hasUsers()) {
            return;
        }
        if (userService.registerWithPasswordHash(LOGIN, LOGIN, PASSWORD_HASH, User.Role.OPERADOR)) {
            log.info("[OperatorLigiaSeed] Operador '{}' criado.", LOGIN);
        }
        settings.set(DONE_FLAG, "true");
    }
}
