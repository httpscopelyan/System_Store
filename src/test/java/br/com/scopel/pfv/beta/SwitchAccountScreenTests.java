package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.BetaApplication;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.UserService;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.FlowPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.awt.GraphicsEnvironment;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class SwitchAccountScreenTests {

    private static final String TARGET_ACCOUNT_PASSWORD = "Senha-FX-" + "2026";

    @Autowired private ApplicationContext context;
    @Autowired private UserService userService;
    @Autowired private UserSession userSession;
    @Autowired private JdbcTemplate jdbc;

    private SaleScreenHarness screen;
    private User targetAccount;
    private ConfigurableApplicationContext previousSpringContext;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void createTargetAccount() {
        previousSpringContext = BetaApplication.springContext;
        BetaApplication.springContext = (ConfigurableApplicationContext) context;
        User account = new User();
        account.setName("Conta FX " + UUID.randomUUID().toString().substring(0, 8));
        account.setLogin("fxconta_" + UUID.randomUUID());
        account.setPassword(TARGET_ACCOUNT_PASSWORD);
        account.setRole(User.Role.OPERADOR);
        account.setActive(true);
        targetAccount = userService.save(account);
    }

    @AfterEach
    void cleanUp() throws Exception {
        BetaApplication.springContext = previousSpringContext;
        screen.close();
        jdbc.update("delete from cash_register where operator_id = ?", targetAccount.getId());
        jdbc.update("delete from audit_log where actor_id = ?", targetAccount.getId());
        jdbc.update("delete from users where id = ?", targetAccount.getId());
    }

    private Button cardOfTargetAccount() throws Exception {
        AtomicReference<Button> card = new AtomicReference<>();
        screen.onFx(() -> {
            FlowPane cards = screen.node("accountCardsPane");
            cards.getChildren().stream()
                    .filter(child -> child instanceof Button button && button.getText().startsWith(targetAccount.getName()))
                    .findFirst().ifPresent(child -> card.set((Button) child));
        });
        return card.get();
    }

    private void choseTargetAccountAndType(String password) throws Exception {
        screen.onFx(() -> cardOfTargetAccount0().fire());
        screen.settle();
        screen.type("passwordField", password);
    }

    private Button cardOfTargetAccount0() {
        FlowPane cards = screen.node("accountCardsPane");
        return (Button) cards.getChildren().stream()
                .filter(child -> child instanceof Button button && button.getText().startsWith(targetAccount.getName()))
                .findFirst().orElseThrow();
    }

    @Test
    void everyActiveAccountAppearsAsACardWithoutExposingThePassword() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        screen.showPage("switch_account");

        Button card = cardOfTargetAccount();
        assertTrue(card != null, "a conta ativa deve aparecer como card");
        assertTrue(card.getText().contains("OPERADOR"));
        assertTrue(!card.getText().contains(TARGET_ACCOUNT_PASSWORD));
    }

    @Test
    void choosingACardShowsOnlyTheSelectedAccountNameAndThePasswordField() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        screen.showPage("switch_account");

        screen.onFx(() -> cardOfTargetAccount0().fire());
        screen.settle();

        assertEquals(targetAccount.getName(), screen.text("selectedAccountLabel"));
        assertTrue(screen.<javafx.scene.Node>node("passwordPanel").isVisible());
        assertTrue(!screen.<javafx.scene.Node>node("accountsPanel").isVisible());
    }

    @Test
    void wrongPasswordKeepsTheCurrentSessionAndClearsTheField() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        User operatorBefore = userSession.getLoggedUser();
        screen.showPage("switch_account");

        choseTargetAccountAndType("senha-errada");
        screen.onFx(() -> ((Button) screen.root().lookupAll(".success-button").iterator().next()).fire());
        screen.settle();

        assertEquals(operatorBefore.getId(), userSession.getLoggedUser().getId());
        assertEquals("", screen.text("passwordField"));
        assertTrue(screen.text("messageLabel").contains("Senha incorreta"));
    }

    @Test
    void correctPasswordSwitchesTheSessionToTheChosenAccountAndDropsTheOldCashRegister() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        Long operatorBeforeId = userSession.getLoggedUser().getId();
        screen.showPage("switch_account");

        choseTargetAccountAndType(TARGET_ACCOUNT_PASSWORD);
        screen.onFx(() -> ((Button) screen.root().lookupAll(".success-button").iterator().next()).fire());
        screen.settle();

        assertEquals(targetAccount.getId(), userSession.getLoggedUser().getId());
        assertNotEquals(operatorBeforeId, userSession.getLoggedUser().getId());
        assertNull(userSession.getCaixaAtual(), "a nova conta não herda o caixa aberto da anterior");
        assertNull(userSession.getVendaAtual());
    }

    @Test
    void aSaleInProgressBlocksTheSwitchAndHidesTheAccountCards() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        Sale saleInPayment = new Sale();
        saleInPayment.setStatus(Sale.Status.EM_PAGAMENTO);
        userSession.setVendaAtual(saleInPayment);
        screen.showPage("switch_account");

        assertTrue(screen.text("messageLabel").contains("venda em andamento"));
        assertTrue(!screen.<javafx.scene.Node>node("accountsPanel").isVisible());
        assertEquals(0, ((FlowPane) screen.node("accountCardsPane")).getChildren().size());
    }
}
