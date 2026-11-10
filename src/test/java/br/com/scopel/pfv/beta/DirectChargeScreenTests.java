package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.model.User;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class DirectChargeScreenTests {

    @Autowired private ApplicationContext context;

    private SaleScreenHarness screen;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        FxTestSupport.startToolkit();
    }

    @AfterEach
    void closeScreen() throws Exception {
        screen.close();
    }

    private void tap(String key) {
        screen.root().lookupAll(".calc-key").stream()
                .map(node -> (Button) node)
                .filter(button -> key.equals(button.getUserData()))
                .findFirst().orElseThrow().fire();
    }

    private String display() {
        return ((Label) screen.node("displayLabel")).getText();
    }

    @Test
    void keypadBuildsTheAmountOnTheDisplay() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        screen.showPage("direct_charge");

        screen.onFx(() -> {
            assertEquals("R$ 0", display());
            tap("2");
            tap("5");
            tap(",");
            tap("5");
            assertEquals("R$ 25,5", display());
            tap("⌫");
            assertEquals("R$ 25,", display());
            tap("C");
            assertEquals("R$ 0", display());
        });
    }

    @Test
    void enterGoesToPaymentChoiceOnlyWhenTheTerminalIsInPdvMode() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        screen.showPage("direct_charge");

        screen.onFx(() -> {
            tap("9");
            ((Button) screen.root().lookup(".calc-enter")).fire();
            if (context.getBean(CardPaymentGateway.class).isIntegrated()) {
                assertTrue(screen.node("choosePanel").isVisible());
                assertFalse(screen.node("inputPanel").isVisible());
            } else {
                assertTrue(screen.node("inputPanel").isVisible());
                assertFalse(screen.node("choosePanel").isVisible());
                assertTrue(((Label) screen.node("messageLabel")).getText().contains("modo PDV"));
            }
        });
    }

    private void typeKeyOn(String nodeId, String character, KeyCode code) throws Exception {
        screen.onFx(() -> screen.node(nodeId).fireEvent(
                new KeyEvent(KeyEvent.KEY_PRESSED, character, character, code, false, false, false, false)));
        screen.settle();
    }

    @Test
    void noteFieldAcceptsAtMost155Characters() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        screen.showPage("direct_charge");

        screen.type("noteField", "x".repeat(200));

        assertEquals(155, screen.text("noteField").length());
    }

    @Test
    void digitsTypedInTheNoteFieldDoNotChangeTheChargeAmount() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        screen.showPage("direct_charge");

        screen.focus("noteField");
        typeKeyOn("noteField", "5", KeyCode.DIGIT5);

        screen.onFx(() -> assertEquals("R$ 0", display()));
    }

    @Test
    void digitsTypedOutsideTheNoteFieldStillBuildTheChargeAmount() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        screen.showPage("direct_charge");

        screen.focus("root");
        typeKeyOn("root", "5", KeyCode.DIGIT5);

        screen.onFx(() -> assertEquals("R$ 5", display()));
        assertTrue(((TextField) screen.node("noteField")).getText().isEmpty());
    }
}
