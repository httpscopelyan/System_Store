package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.controller.support.SaleFocusGuard;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.awt.GraphicsEnvironment;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf("isHeadless")
class SaleFocusGuardTests {

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        FxTestSupport.startToolkit();
    }

    @Test
    void lostFocusWithNoOwnerIsReclaimed_RF73() throws Exception {
        FxTestSupport.runOnFx(() -> assertTrue(SaleFocusGuard.shouldReclaim(null, Set.of())));
    }

    @Test
    void focusOnThePassiveTableIsReclaimed_RF73() throws Exception {
        FxTestSupport.runOnFx(() -> {
            TableView<String> table = new TableView<>();
            assertTrue(SaleFocusGuard.shouldReclaim(table, Set.<Node>of(table)));
        });
    }

    @Test
    void inputsButtonsAndChoicesKeepTheFocus_RF73() throws Exception {
        FxTestSupport.runOnFx(() -> {
            Set<Node> passive = Set.of(new TableView<String>());
            assertFalse(SaleFocusGuard.shouldReclaim(new TextField(), passive));
            assertFalse(SaleFocusGuard.shouldReclaim(new Button(), passive));
            assertFalse(SaleFocusGuard.shouldReclaim(new CheckBox(), passive));
            assertFalse(SaleFocusGuard.shouldReclaim(new ComboBox<String>(), passive));
            assertFalse(SaleFocusGuard.shouldReclaim(new ListView<String>(), passive));
        });
    }
}
