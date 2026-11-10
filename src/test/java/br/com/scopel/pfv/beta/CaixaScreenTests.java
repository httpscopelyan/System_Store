package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.User;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class CaixaScreenTests {

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserSession userSession;

    private SaleScreenHarness screen;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void openCaixaScreen() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        screen.showPage("caixa");
    }

    @AfterEach
    void closeScreen() throws Exception {
        screen.close();
    }

    private void click(String buttonText) throws Exception {
        screen.onFx(() -> {
            for (Node node : screen.root().lookupAll(".button")) {
                if (node instanceof Button button && button.getText() != null && button.getText().contains(buttonText)) {
                    button.fire();
                    return;
                }
            }
            throw new IllegalStateException("Botão não encontrado: " + buttonText);
        });
        screen.settle();
    }

    private void typeReason(String reason) throws Exception {
        screen.onFx(() -> {
            ComboBox<String> box = screen.node("movementReasonBox");
            box.getEditor().setText(reason);
        });
        screen.settle();
    }

    private List<Map<String, Object>> movements() {
        return jdbc.queryForList("select type, amount, reason from cash_movement order by id desc limit 5");
    }

    @Test
    void withdrawalWithoutAReasonIsRefusedOnTheScreen_RN28() throws Exception {
        screen.type("movementAmountField", "10");
        click("Sangria");
        assertTrue(screen.text("statusLabel").contains("motivo"));
    }

    @Test
    void withdrawalWithAReasonIsRegisteredWithIt_RN28() throws Exception {
        screen.type("movementAmountField", "10");
        typeReason("Pagamento a fornecedor");
        click("Sangria");
        assertEquals("Sangria registrada!", screen.text("statusLabel"));
        Map<String, Object> saved = movements().get(0);
        assertEquals("SANGRIA", saved.get("type"));
        assertEquals("Pagamento a fornecedor", saved.get("reason"));
    }

    @Test
    void supplementWithAReasonClearsTheFormAndShowsTheMovement_RN28() throws Exception {
        screen.type("movementAmountField", "25");
        typeReason("Troco inicial");
        click("Suprimento");
        assertEquals("Suprimento registrado!", screen.text("statusLabel"));
        assertEquals("", screen.text("movementAmountField"));
        assertEquals(1, ((javafx.scene.control.TableView<?>) screen.node("movTable")).getItems().size());
    }

    @Test
    void rectifyWithoutASelectedMovementExplainsWhatToDo_RN29() throws Exception {
        click("Retificar");
        assertTrue(screen.text("statusLabel").contains("Selecione"));
        assertEquals(CashMovement.Type.values().length, 3);
    }

    @Test
    void reasonPresetsAreOfferedInTheCombo_RN28() throws Exception {
        screen.onFx(() -> {
            ComboBox<String> box = screen.node("movementReasonBox");
            assertTrue(box.getItems().contains("Troco inicial"));
            assertTrue(box.getItems().contains("Depósito bancário"));
            assertTrue(box.getItems().contains("Outro"));
            assertTrue(box.isEditable());
        });
    }

    private void fillClosingCounts(String drawer, String pix, String credit, String debit, String extra)
            throws Exception {
        screen.type("countedDrawerField", drawer);
        screen.type("countedPixField", pix);
        screen.type("countedCreditField", credit);
        screen.type("countedDebitField", debit);
        screen.type("countedExtraField", extra);
    }

    private Map<String, Object> registerOfTheLoggedOperator() {
        return jdbc.queryForMap("select status, counted_extra, extra_note from cash_register "
                + "where operator_id = ? order by id desc limit 1", userSession.getLoggedUser().getId());
    }

    @Test
    void extraNoteBecomesRequiredOnlyWhileTheCountedExtraDiffersFromTheExpected() throws Exception {
        screen.type("countedExtraField", "5");
        assertTrue(screen.text("extraNoteLabel").contains("obrigatória"));

        screen.type("countedExtraField", "0");
        assertEquals("Observação do Extra", screen.text("extraNoteLabel"));
    }

    @Test
    void closingWithADifferentExtraAndNoNoteIsRefusedAndTheRegisterStaysOpen() throws Exception {
        fillClosingCounts("50", "0", "0", "0", "5");

        click("Fechar Caixa");

        assertTrue(screen.text("statusLabel").contains("Observação do Extra"));
        assertEquals("OPEN", String.valueOf(registerOfTheLoggedOperator().get("status")));
    }

    @Test
    void closingWithAMissingCountIsRefusedAndTheRegisterStaysOpen() throws Exception {
        screen.type("countedDrawerField", "50");

        click("Fechar Caixa");

        assertTrue(screen.text("statusLabel").contains("Informe o valor contado"));
        assertEquals("OPEN", String.valueOf(registerOfTheLoggedOperator().get("status")));
    }

    @Test
    void closingWithEveryCountMatchingClosesTheRegister() throws Exception {
        fillClosingCounts("50", "0", "0", "0", "0");

        click("Fechar Caixa");

        assertTrue(screen.text("statusLabel").startsWith("Caixa FECHADO"));
        assertEquals("CLOSED", String.valueOf(registerOfTheLoggedOperator().get("status")));
    }

    @Test
    void closingWithADifferentExtraAndANoteStoresTheNoteWithTheCountedValue() throws Exception {
        fillClosingCounts("50", "0", "0", "0", "5");
        screen.type("extraNoteField", "Cobrança feita na maquininha sem registro");

        click("Fechar Caixa");

        assertTrue(screen.text("statusLabel").startsWith("Caixa FECHADO"));
        Map<String, Object> closed = registerOfTheLoggedOperator();
        assertEquals("Cobrança feita na maquininha sem registro", closed.get("extra_note"));
        assertEquals(0, new java.math.BigDecimal("5").compareTo((java.math.BigDecimal) closed.get("counted_extra")));
    }
}
