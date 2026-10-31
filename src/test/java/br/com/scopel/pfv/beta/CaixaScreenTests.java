package br.com.scopel.pfv.beta;

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
}
