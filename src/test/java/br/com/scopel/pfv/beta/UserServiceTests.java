package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.UserDuplicatedException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserServiceTests {

    @Autowired
    private UserService userService;

    // 1. A SENHA É GUARDADA COMO HASH, NÃO EM TEXTO PURO (o teste mais importante)
    @Test
    void passwordIsHashedOnSave() {
        User user = new User();
        user.setName("Joao");
        user.setLogin("joao1");
        user.setPassword("senha123");      // senha pura
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);

        User saved = userService.save(user);

        assertNotEquals("senha123", saved.getPassword());
        assertTrue(saved.getPassword().startsWith("$2"));
    }

    @Test
    void loginWithCorrectPassword() {
        User user = new User();
        user.setName("Maria");
        user.setLogin("maria1");
        user.setPassword("abc123");
        user.setRole(User.Role.GERENTE);
        user.setActive(true);
        userService.save(user);

        User logged = userService.login("maria1", "abc123");

        assertNotNull(logged);
        assertEquals("maria1", logged.getLogin());
    }

    @Test
    void loginWithWrongPassword() {
        User user = new User();
        user.setName("Carlos");
        user.setLogin("carlos1");
        user.setPassword("certa123");
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);
        userService.save(user);

        assertThrows(InvalidPasswordException.class, () -> {
            userService.login("carlos1", "errada999");
        });
    }

    @Test
    void loginWithNonexistentUser() {
        assertThrows(UserNotFindException.class, () -> {
            userService.login("naoexiste", "qualquer");
        });
    }

    // 5. LOGIN DUPLICADO lança exceção ao cadastrar
    @Test
    void saveDuplicateLogin() {
        User u1 = new User();
        u1.setName("Ana");
        u1.setLogin("ana1");
        u1.setPassword("senha1");
        u1.setRole(User.Role.OPERADOR);
        u1.setActive(true);
        userService.save(u1);

        User u2 = new User();
        u2.setName("Ana Segunda");
        u2.setLogin("ana1");           // mesmo login
        u2.setPassword("senha2");
        u2.setRole(User.Role.OPERADOR);
        u2.setActive(true);

        assertThrows(UserDuplicatedException.class, () -> {
            userService.save(u2);
        });
    }
}