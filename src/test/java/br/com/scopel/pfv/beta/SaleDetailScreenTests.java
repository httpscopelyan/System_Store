package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.SaleDetailAction;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.awt.GraphicsEnvironment;
import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class SaleDetailScreenTests {

    @Autowired private ApplicationContext context;
    @Autowired private UserSession userSession;
    @Autowired private SaleService saleService;

    private SaleScreenHarness screen;
    private String productCode;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void prepare() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR, "Operador tela detalhe");
        productCode = screen.createProduct("Feijao tela", Product.Unidade.UN, "8.00");
    }

    @AfterEach
    void cleanUp() throws Exception {
        screen.onFx(() -> new ArrayList<>(Window.getWindows()).stream()
                .filter(window -> window.getScene() != null && window.getScene().getRoot() != null
                        && window.getScene().getRoot().getClass().getSimpleName().equals("DialogPane"))
                .forEach(Window::hide));
        screen.close();
    }

    private Sale finalizedCashSale() {
        Sale sale = saleService.openSale(userSession.getLoggedUser(), userSession.getCaixaAtual());
        sale = saleService.addItem(sale, productCode, new BigDecimal("2"));
        sale = saleService.goToPayment(sale);
        return saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal("20.00"));
    }

    private void click(String buttonText) throws Exception {
        screen.onFx(() -> screen.root().lookupAll(".button").stream()
                .filter(node -> node instanceof Button button && button.getText() != null
                        && button.getText().contains(buttonText))
                .map(node -> (Button) node).findFirst().orElseThrow().fire());
        screen.settle();
    }

    private boolean isSaleDetailDialogShowing() throws Exception {
        boolean[] showing = new boolean[1];
        screen.onFx(() -> showing[0] = Window.getWindows().stream()
                .anyMatch(window -> window.isShowing() && window.getScene() != null
                        && window.getScene().getRoot().getClass().getSimpleName().equals("DialogPane")));
        return showing[0];
    }

    @Test
    void historyWithoutASelectionAsksToSelectASale() throws Exception {
        screen.showPage("historico_vendas");

        click("Ver itens");

        assertEquals(SaleDetailAction.NO_SALE_SELECTED_MESSAGE, screen.text("statusLabel"));
    }

    @Test
    void historyShowsTheDetailDialogOfTheSelectedSale() throws Exception {
        Sale finalizedSale = finalizedCashSale();
        screen.showPage("historico_vendas");
        screen.onFx(() -> {
            TableView<Sale> historyTable = screen.node("table");
            historyTable.getSelectionModel().select(historyTable.getItems().stream()
                    .filter(sale -> sale.getId().equals(finalizedSale.getId())).findFirst().orElseThrow());
        });

        Platform.runLater(() -> screen.root().lookupAll(".button").stream()
                .filter(node -> node instanceof Button button && "🔍 Ver itens".equals(button.getText()))
                .map(node -> (Button) node).findFirst().orElseThrow().fire());
        screen.pause(800);

        assertTrue(isSaleDetailDialogShowing());
        int[] itemRows = new int[1];
        screen.onFx(() -> Window.getWindows().stream()
                .filter(window -> window.isShowing() && window.getScene() != null
                        && window.getScene().getRoot().getClass().getSimpleName().equals("DialogPane"))
                .map(window -> (TableView<?>) window.getScene().getRoot().lookup(".items-table"))
                .findFirst().ifPresent(itemsTable -> itemRows[0] = itemsTable.getItems().size()));
        assertEquals(1, itemRows[0]);
    }

    @Test
    void cashScreenRefusesToShowItemsForARowThatIsNotASale() throws Exception {
        screen.showPage("caixa");

        click("Ver itens");

        assertEquals(SaleDetailAction.NO_SALE_SELECTED_MESSAGE, screen.text("statusLabel"));
    }
}
