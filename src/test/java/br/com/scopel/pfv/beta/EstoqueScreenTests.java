package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.PricingSuggestionService;
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
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class EstoqueScreenTests {

    @Autowired private ApplicationContext context;
    @Autowired private ProductRepository productRepository;
    @Autowired private PricingSuggestionService pricingSuggestionService;

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

    private TableColumn<Product, ?> column(String header) {
        return table().getColumns().stream().filter(column -> header.equals(column.getText())).findFirst().orElseThrow();
    }

    @Test
    void managerSeesCostAndProfitColumnsAndTheMarginsToggle_RF64() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.GERENTE);
        screen.showPage("estoque");
        screen.onFx(() -> {
            assertTrue(column("Custo").isVisible());
            assertTrue(column("Lucro (R$)").isVisible());
            assertTrue(column("Lucro (%)").isVisible());
            assertTrue(column("Preço sugerido").isVisible());
            assertTrue(screen.node("marginsToggle").isVisible());
        });
    }

    @Test
    void operatorDoesNotSeeCostOrProfit_RF64() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        screen.showPage("estoque");
        screen.onFx(() -> {
            assertFalse(column("Custo").isVisible());
            assertFalse(column("Lucro (R$)").isVisible());
            assertFalse(column("Lucro (%)").isVisible());
            assertFalse(column("Preço sugerido").isVisible());
            assertTrue(column("Vr. Venda").isVisible());
            assertFalse(screen.node("marginsToggle").isVisible());
        });
    }

    @Test
    void marginsToggleHidesAndShowsTheManagerColumns_RF64() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.GERENTE);
        screen.showPage("estoque");
        screen.onFx(() -> {
            ((javafx.scene.control.ToggleButton) screen.node("marginsToggle")).fire();
            assertFalse(column("Custo").isVisible());
            ((javafx.scene.control.ToggleButton) screen.node("marginsToggle")).fire();
            assertTrue(column("Custo").isVisible());
        });
    }

    @Test
    void acceptSuggestionIsOnlyEnabledWhenTheSelectionIsInTheSalePriceColumn_RF63() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.GERENTE);
        screen.createProduct("Monster teste", Product.Unidade.UN, "1.00");
        screen.showPage("estoque");
        screen.onFx(() -> {
            Button accept = screen.node("acceptSuggestionButton");
            assertTrue(accept.isDisabled());
            table().getSelectionModel().select(0, column("Vr. Venda"));
            assertFalse(accept.isDisabled());
            table().getSelectionModel().select(0, column("Custo"));
            assertTrue(accept.isDisabled());
        });
    }

    @Test
    void acceptSuggestionAppliesTheComputedPriceToTheSelectedProducts_RF63() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.GERENTE);
        String code = screen.createProduct("Monster aceitar", Product.Unidade.UN, "1.00");
        Product created = productRepository.findByCodeIgnoreCase(code).orElseThrow();
        created.setCostPrice(new BigDecimal("10.00"));
        productRepository.save(created);
        screen.showPage("estoque");
        screen.type("codeFilter", code);
        screen.submit("codeFilter");
        screen.onFx(() -> {
            assertEquals(1, table().getItems().size());
            table().getSelectionModel().select(0, column("Vr. Venda"));
            ((Button) screen.node("acceptSuggestionButton")).fire();
        });
        screen.settle();
        assertEquals(pricingSuggestionService.suggestFor(new BigDecimal("10.00")),
                productRepository.findByCodeIgnoreCase(code).orElseThrow().getPrice());
    }

    @Test
    void quantityColumnIsNotEditableOnTheScreen_RN26() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.GERENTE);
        screen.showPage("estoque");
        screen.onFx(() -> {
            assertFalse(column("Qtd. Atual").isEditable());
            assertTrue(column("Vr. Venda").isEditable());
            assertTrue(table().getSelectionModel().isCellSelectionEnabled());
        });
    }
}
