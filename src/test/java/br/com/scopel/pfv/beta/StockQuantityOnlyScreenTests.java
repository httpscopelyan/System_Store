package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class StockQuantityOnlyScreenTests {

    private static final List<String> LOCKED_ACTIONS = List.of("＋ Novo", "✎ Alterar", "－ Apagar", "➕ Entrada", "📅 Lotes");

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

    @SuppressWarnings("unchecked")
    private TableView<Product> table() {
        return (TableView<Product>) screen.node("table");
    }

    private boolean isColumnEditable(String header) {
        TableColumn<Product, ?> column = table().getColumns().stream()
                .filter(candidate -> header.equals(candidate.getText())).findFirst().orElseThrow();
        return column.isEditable();
    }

    private boolean isActionVisible(String buttonText) throws Exception {
        AtomicBoolean visible = new AtomicBoolean();
        screen.onFx(() -> {
            Node button = screen.root().lookupAll(".button").stream()
                    .filter(candidate -> candidate instanceof Button labelled && buttonText.equals(labelled.getText()))
                    .findFirst().orElseThrow();
            visible.set(button.isVisible() && button.isManaged());
        });
        return visible.get();
    }

    @Test
    void anaClaraCanEditOnlyTheStockQuantityColumn() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR, "Ana Clara");
        screen.showPage("estoque");

        assertTrue(isColumnEditable("Qtd. Atual"));
        for (String lockedColumn : List.of("Código", "Nome", "Vr. Venda", "Qtd. Mín.")) {
            assertFalse(isColumnEditable(lockedColumn), "coluna deve ficar bloqueada: " + lockedColumn);
        }
    }

    @Test
    void anaClaraKeepsTheAdjustmentButtonAndLosesTheOtherEditingActions() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR, "Ana Clara");
        screen.showPage("estoque");

        assertTrue(isActionVisible("⚖ Ajuste"));
        for (String lockedAction : LOCKED_ACTIONS) {
            assertFalse(isActionVisible(lockedAction), "ação deve ficar oculta: " + lockedAction);
        }
    }

    @Test
    void anOrdinaryOperatorKeepsEveryActionVisible() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR, "Operador comum");
        screen.showPage("estoque");

        for (String action : LOCKED_ACTIONS) {
            assertTrue(isActionVisible(action), "ação deve continuar visível: " + action);
        }
    }
}
