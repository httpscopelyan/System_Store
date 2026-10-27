package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialUserBootstrapTests {

    @TempDir
    Path dir;

    private Path env;
    private UserService userService;

    @BeforeEach
    void setUp() throws Exception {
        env = dir.resolve(".env");
        Files.writeString(env, "DB_NAME=db_scopel\npdv.bootstrap.login=YAN\npdv.bootstrap.name=Yan\n"
                + "pdv.bootstrap.password=senha-inicial\nDB_PASSWORD=segredo\n", StandardCharsets.UTF_8);
        userService = mock(UserService.class);
    }

    private InitialUserBootstrap bootstrap(String login, String password) {
        return new InitialUserBootstrap(userService, login, "Yan", password, env.toString());
    }

    @Test
    void emptyDatabaseCreatesManagerAndErasesBootstrapKeysFromEnv() throws Exception {
        when(userService.hasUsers()).thenReturn(false);

        bootstrap("YAN", "senha-inicial").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userService).save(saved.capture());
        assertEquals("YAN", saved.getValue().getLogin());
        assertEquals(User.Role.GERENTE, saved.getValue().getRole());
        assertEquals("senha-inicial", saved.getValue().getPassword(), "o hash é feito pelo UserService");

        String remaining = Files.readString(env);
        assertFalse(remaining.contains("pdv.bootstrap"), "senha inicial não pode ficar no .env");
        assertTrue(remaining.contains("DB_NAME=db_scopel") && remaining.contains("DB_PASSWORD=segredo"),
                "o resto da configuração é preservado");
    }

    @Test
    void databaseWithUsersIsNotTouchedButBootstrapKeysAreStillErased() throws Exception {
        when(userService.hasUsers()).thenReturn(true);

        bootstrap("YAN", "senha-inicial").run(null);

        verify(userService, never()).save(any());
        assertFalse(Files.readString(env).contains("pdv.bootstrap"));
    }

    @Test
    void withoutBootstrapPasswordNothingHappensAndEnvIsUntouched() throws Exception {
        String before = Files.readString(env);

        bootstrap("YAN", "").run(null);

        verify(userService, never()).hasUsers();
        verify(userService, never()).save(any());
        assertEquals(before, Files.readString(env));
    }
}
