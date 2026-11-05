package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(2)
@Slf4j
public class OperatorSeed implements ApplicationRunner {

    record SeededOperator(String login, String name, String passwordHash) {
    }

    static final List<SeededOperator> OPERATORS = List.of(
            new SeededOperator("Dayana", "Dayana", "$2a$10$npOry.e8FSL4.kZrD9jAnuYiBNiVZ6sLQN8AjZIoOeEuL8FKRXmfG"),
            new SeededOperator("Maria", "Maria", "$2a$10$QtJVRI4bklF3ebwo2iESlu7SvMRWezA3W7ih8TR6j.UeUsij3aFEa"));

    private final UserService userService;

    public OperatorSeed(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userService.hasUsers()) {
            return;
        }
        for (SeededOperator operator : OPERATORS) {
            boolean created = userService.registerWithPasswordHash(
                    operator.login(), operator.name(), operator.passwordHash(), User.Role.OPERADOR);
            if (created) {
                log.info("[OperatorSeed] Operador '{}' criado.", operator.login());
            }
        }
    }
}
