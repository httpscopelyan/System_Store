package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Cria o primeiro usuário (GERENTE) de um banco vazio, a partir de pdv.bootstrap.* (vindo do .env
 * gravado pelo instalador). A senha passa pelo {@link UserService} (BCrypt) e as chaves são apagadas
 * do .env em seguida, para a senha em texto puro não ficar em disco. Banco que já tem usuário: nada a fazer.
 */
@Component
@Slf4j
public class InitialUserBootstrap implements ApplicationRunner {

    static final String KEY_PREFIX = "pdv.bootstrap.";

    private final UserService userService;
    private final String login;
    private final String name;
    private final String password;
    private final Path envFile;

    public InitialUserBootstrap(UserService userService,
                                @Value("${pdv.bootstrap.login:}") String login,
                                @Value("${pdv.bootstrap.name:}") String name,
                                @Value("${pdv.bootstrap.password:}") String password,
                                @Value("${pdv.bootstrap.env-file:.env}") String envFile) {
        this.userService = userService;
        this.login = login;
        this.name = name;
        this.password = password;
        this.envFile = Path.of(envFile);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank() || login.isBlank()) {
            return;
        }
        if (userService.hasUsers()) {
            log.info("[Bootstrap] O banco já tem usuários; credenciais iniciais ignoradas.");
        } else {
            User master = new User();
            master.setLogin(login.trim());
            master.setName((name.isBlank() ? login : name).trim());
            master.setPassword(password);
            master.setRole(User.Role.GERENTE);
            userService.save(master);
            log.info("[Bootstrap] Usuário inicial '{}' criado como GERENTE.", master.getLogin());
        }
        removeBootstrapKeys();
    }

    private void removeBootstrapKeys() {
        try {
            if (!Files.isRegularFile(envFile)) {
                return;
            }
            List<String> kept = Files.readAllLines(envFile, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.strip().startsWith(KEY_PREFIX))
                    .toList();
            Files.write(envFile, kept, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[Bootstrap] Não foi possível apagar a senha inicial do .env ({}); remova as linhas "
                    + "'{}*' manualmente.", e.getMessage(), KEY_PREFIX);
        }
    }
}
