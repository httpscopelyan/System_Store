package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.UserDuplicatedException;
import br.com.scopel.pfv.beta.model.AccountSummary;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.AdministratorYanSeed;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.StaffPermissions;
import br.com.scopel.pfv.beta.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AdministratorAccountTests {

    private static final String ADMINISTRATOR_LOGIN = "Yan";
    private static final String SEED_DONE_FLAG = "seed.administrator_yan.done";
    private static final String BCRYPT_FORMAT = "^\\$2a\\$10\\$[./A-Za-z0-9]{53}$";

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private AppSettingService settings;
    @Autowired private AdministratorYanSeed administratorYanSeed;

    private User administrator;

    @BeforeEach
    void prepare() {
        settings.set(SEED_DONE_FLAG, "false");
        administratorYanSeed.run(null);
        administrator = userService.findLogin(ADMINISTRATOR_LOGIN);
    }

    private User newUser(String login, String password, User.Role role) {
        User user = new User();
        user.setName(login);
        user.setLogin(login);
        user.setPassword(password);
        user.setRole(role);
        return userService.save(user);
    }

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private long accountsNamedYan() {
        return userRepository.findAll().stream()
                .filter(user -> ADMINISTRATOR_LOGIN.equals(user.getLogin())).count();
    }

    @Test
    void seedCreatesTheAdministratorAsDevWithABcryptHash() {
        assertEquals(User.Role.DEV, administrator.getRole());
        assertEquals(ADMINISTRATOR_LOGIN, administrator.getName());
        assertTrue(administrator.getActive());
        assertTrue(administrator.getPassword().matches(BCRYPT_FORMAT));
        assertTrue(StaffPermissions.isAdministrator(administrator));
        assertTrue(settings.getBoolean(SEED_DONE_FLAG, false));
    }

    @Test
    void seedIsIdempotentAndNeverDuplicatesOrRewritesTheAccount() {
        String hashBefore = administrator.getPassword();

        settings.set(SEED_DONE_FLAG, "false");
        administratorYanSeed.run(null);
        administratorYanSeed.run(null);

        assertEquals(1, accountsNamedYan());
        assertEquals(hashBefore, userService.findLogin(ADMINISTRATOR_LOGIN).getPassword());
        assertTrue(settings.getBoolean(SEED_DONE_FLAG, false));
    }

    @Test
    void loginWithAWrongPasswordFails() {
        assertThrows(InvalidPasswordException.class, () -> userService.login(ADMINISTRATOR_LOGIN, "senha-errada"));
        assertThrows(InvalidPasswordException.class, () -> userService.login(ADMINISTRATOR_LOGIN, ""));
        assertThrows(InvalidPasswordException.class,
                () -> userService.authenticateManager(ADMINISTRATOR_LOGIN, ADMINISTRATOR_LOGIN));
    }

    @Test
    void anotherManagerCanNotRenameIntoTheAdministratorName() {
        String managerLogin = unique("ger");
        User manager = newUser(managerLogin, "senha1", User.Role.GERENTE);

        assertThrows(UserDuplicatedException.class,
                () -> userService.updateOwnCredentials(manager.getId(), "senha1", "Yan", null));
        assertThrows(UserDuplicatedException.class,
                () -> userService.updateOwnCredentials(manager.getId(), "senha1", "  yan ", null));
        assertThrows(UserDuplicatedException.class,
                () -> userService.updateOwnCredentials(manager.getId(), "senha1", "YAN", null));

        assertEquals(managerLogin, userService.findLogin(managerLogin).getLogin());
        assertFalse(StaffPermissions.isAdministrator(userService.findLogin(managerLogin)));
        assertEquals(1, accountsNamedYan());
    }

    @Test
    void administratorDataOnlyChangesWithTheAdministratorOwnPassword() {
        String hashBefore = administrator.getPassword();

        assertThrows(InvalidPasswordException.class, () -> userService.updateOwnCredentials(administrator.getId(),
                "senha-errada", ADMINISTRATOR_LOGIN, "novaSenha1"));

        assertEquals(hashBefore, userService.findLogin(ADMINISTRATOR_LOGIN).getPassword());
    }

    @Test
    void administratorAccountKeepsItsNameAndItsDevRole() {
        String knownPasswordHash = newUser(unique("tmp"), "senha1", User.Role.OPERADOR).getPassword();
        administrator.setPassword(knownPasswordHash);
        userRepository.save(administrator);

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateOwnCredentials(administrator.getId(), "senha1", unique("novo"), null));
        assertThrows(IllegalArgumentException.class,
                () -> userService.modificationRole(ADMINISTRATOR_LOGIN, User.Role.OPERADOR));

        User reloaded = userService.findLogin(ADMINISTRATOR_LOGIN);
        assertEquals(User.Role.DEV, reloaded.getRole());
        assertEquals(ADMINISTRATOR_LOGIN, reloaded.getName());
    }

    @Test
    void activeAccountsAreListedByNameWithoutEverExposingTheHash() {
        String firstLogin = unique("aaa");
        String inactiveLogin = unique("zzz");
        newUser(firstLogin, "senha1", User.Role.OPERADOR);
        User inactive = newUser(inactiveLogin, "senha1", User.Role.OPERADOR);
        inactive.setActive(false);
        userRepository.save(inactive);

        List<AccountSummary> accounts = userService.listActiveAccounts();

        assertTrue(accounts.stream().anyMatch(account -> account.login().equals(firstLogin)
                && account.role() == User.Role.OPERADOR && account.id() != null));
        assertTrue(accounts.stream().anyMatch(account -> account.login().equals(ADMINISTRATOR_LOGIN)
                && account.role() == User.Role.DEV));
        assertFalse(accounts.stream().anyMatch(account -> account.login().equals(inactiveLogin)));
        List<String> loginsInOrder = accounts.stream().map(AccountSummary::login).toList();
        assertTrue(loginsInOrder.indexOf(firstLogin) < loginsInOrder.indexOf(ADMINISTRATOR_LOGIN));
        assertEquals(List.of("id", "name", "login", "role"),
                Arrays.stream(AccountSummary.class.getRecordComponents()).map(component -> component.getName())
                        .toList());
        assertFalse(accounts.toString().contains("$2a$"));
    }
}
