package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.UserDuplicatedException;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class OwnCredentialsTests {

    @Autowired private UserService userService;

    private User newOperator(String login, String password) {
        User user = new User();
        user.setName(login);
        user.setLogin(login);
        user.setPassword(password);
        user.setRole(User.Role.OPERADOR);
        return userService.save(user);
    }

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void renamedOperatorLogsInWithNewNameAndNewPassword() {
        String oldName = "op" + unique();
        String newName = "nv" + unique();
        User operator = newOperator(oldName, "senha1");

        userService.updateOwnCredentials(operator.getId(), "senha1", newName, "nova99");

        assertEquals(newName, userService.login(newName, "nova99").getName());
        assertThrows(RuntimeException.class, () -> userService.login(oldName, "nova99"));
        assertThrows(InvalidPasswordException.class, () -> userService.login(newName, "senha1"));
    }

    @Test
    void blankNewPasswordKeepsTheCurrentOne() {
        String name = "op" + unique();
        User operator = newOperator(name, "senha1");
        String renamed = "nv" + unique();

        userService.updateOwnCredentials(operator.getId(), "senha1", renamed, "");

        assertEquals(renamed, userService.login(renamed, "senha1").getLogin());
    }

    @Test
    void wrongCurrentPasswordChangesNothing() {
        String name = "op" + unique();
        User operator = newOperator(name, "senha1");

        assertThrows(InvalidPasswordException.class,
                () -> userService.updateOwnCredentials(operator.getId(), "errada", "outro" + unique(), "nova99"));

        assertEquals(name, userService.login(name, "senha1").getLogin());
    }

    @Test
    void nameAlreadyUsedByAnotherUserIsRefused() {
        String first = "op" + unique();
        String second = "op" + unique();
        newOperator(first, "senha1");
        User other = newOperator(second, "senha2");

        assertThrows(UserDuplicatedException.class,
                () -> userService.updateOwnCredentials(other.getId(), "senha2", first, ""));
    }

    @Test
    void keepingTheSameNameIsAllowedAndShortPasswordIsRefused() {
        String name = "op" + unique();
        User operator = newOperator(name, "senha1");

        userService.updateOwnCredentials(operator.getId(), "senha1", name, "");

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateOwnCredentials(operator.getId(), "senha1", name, "abc"));
        assertThrows(IllegalArgumentException.class,
                () -> userService.updateOwnCredentials(operator.getId(), "senha1", "  ", ""));
    }
}
