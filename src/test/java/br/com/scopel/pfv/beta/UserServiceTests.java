package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.UserDuplicatedException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UserServiceTests {

    @Autowired
    private UserService userService;

    // 1. A SENHA É GUARDADA COMO HASH, NÃO EM TEXTO PURO (o teste mais importante)
    @Test
    void passwordIsHashedOnSave() {
        String login = "joao_" + UUID.randomUUID();
        User user = new User();
        user.setName("Joao");
        user.setLogin(login);
        user.setPassword("senha123");      // senha pura
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);

        User saved = userService.save(user);

        assertNotEquals("senha123", saved.getPassword());
        assertTrue(saved.getPassword().startsWith("$2"));
    }

    @Test
    void loginWithCorrectPassword() {
        String login = "maria_" + UUID.randomUUID();
        User user = new User();
        user.setName("Maria");
        user.setLogin(login);
        user.setPassword("abc123");
        user.setRole(User.Role.GERENTE);
        user.setActive(true);
        userService.save(user);

        User logged = userService.login(login, "abc123");

        assertNotNull(logged);
        assertEquals(login, logged.getLogin());
    }

    @Test
    void loginWithWrongPassword() {
        String login = "carlos_" + UUID.randomUUID();
        User user = new User();
        user.setName("Carlos");
        user.setLogin(login);
        user.setPassword("certa123");
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);
        userService.save(user);

        assertThrows(InvalidPasswordException.class, () -> {
            userService.login(login, "errada999");
        });
    }

    @Test
    void loginWithNonexistentUser() {
        assertThrows(UserNotFindException.class, () -> {
            userService.login("naoexiste_" + UUID.randomUUID(), "qualquer");
        });
    }

    // 5. LOGIN DUPLICADO lança exceção ao cadastrar
    @Test
    void saveDuplicateLogin() {
        String login = "ana_" + UUID.randomUUID();

        User u1 = new User();
        u1.setName("Ana");
        u1.setLogin(login);
        u1.setPassword("senha1");
        u1.setRole(User.Role.OPERADOR);
        u1.setActive(true);
        userService.save(u1);

        User u2 = new User();
        u2.setName("Ana Segunda");
        u2.setLogin(login);           // mesmo login
        u2.setPassword("senha2");
        u2.setRole(User.Role.OPERADOR);
        u2.setActive(true);

        assertThrows(UserDuplicatedException.class, () -> {
            userService.save(u2);
        });
    }
}
