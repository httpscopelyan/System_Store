package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.User;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class SaleScreenFocusTests {

    @Autowired
    private ApplicationContext context;

    private SaleScreenHarness screen;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void openSaleScreen() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        screen.showSaleScreen();
    }

    @AfterEach
    void closeSaleScreen() throws Exception {
        screen.close();
    }

    @Test
    void scanFieldStartsFocusedAndEnabledWhenTheCashRegisterIsOpen_RN21() throws Exception {
        assertFalse(screen.isDisabled("codeField"));
        assertSame(screen.node("codeField"), screen.focusOwner());
    }

    @Test
    void everySaleFieldKeepsTheFocusWhenSelected_RF73() throws Exception {
        for (String id : new String[]{"discountField", "customerIdField", "cancelItemCodeField",
                "cancelReasonField"}) {
            screen.focus(id);
            assertSame(screen.node(id), screen.focusOwner(), id);
        }
    }

    @Test
    void focusMovesFreelyBetweenFieldsAndBackToTheScanField_RF73() throws Exception {
        screen.focus("discountField");
        screen.focus("cancelReasonField");
        screen.focus("customerIdField");
        assertSame(screen.node("customerIdField"), screen.focusOwner());
        screen.focus("codeField");
        assertSame(screen.node("codeField"), screen.focusOwner());
    }

    @Test
    void typingInTheDiscountFieldIsKept_RF73() throws Exception {
        screen.focus("discountField");
        screen.type("discountField", "2,50");
        assertEquals("2,50", screen.text("discountField"));
        assertSame(screen.node("discountField"), screen.focusOwner());
    }

    @Test
    void sideButtonsAreEnabledAndReceiveMouseEvents_RF73() throws Exception {
        for (Node node : screen.root().lookupAll(".button")) {
            Button button = (Button) node;
            assertFalse(button.isDisabled(), button.getText());
            assertFalse(button.isMouseTransparent(), button.getText());
        }
    }

    @Test
    void actionsWithoutASaleReportInsteadOfStayingSilent_RF73() throws Exception {
        screen.onFx(() -> ((Button) screen.root().lookup(".finalize-button")).fire());
        screen.settle();
        assertTrue(screen.text("statusLabel").length() > 0);
    }
}
