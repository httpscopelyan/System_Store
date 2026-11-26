package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.ProductService;
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
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class HundredGramsAndHiddenCostScreenTests {

    @Autowired private ApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSession userSession;
    @Autowired private ProductService productService;

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
        userSession.setProdutoEmEdicaoId(null);
        screen.close();
    }

    private void grant(EnumSet<Permission> permissions) {
        User user = userSession.getLoggedUser();
        user.setPermissions(permissions);
        userSession.setLoggedUser(userRepository.save(user));
    }

    private List<String> visibleColumns() {
        TableView<?> table = screen.node("table");
        return table.getColumns().stream().filter(TableColumn::isVisible).map(TableColumn::getText).toList();
    }

    @Test
    void aWeightProductPricedPerHundredGramsIsTypedInGrams() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        String code = screen.createProduct("Queijo fatiado", Product.Unidade.G100, "5.99");
        screen.showSaleScreen();
        screen.type("codeField", code);
        screen.submit("codeField");

        assertEquals("Peso (gramas)", screen.text("quantityLabel"));
        screen.type("quantityField", "250");
        assertEquals("250 g × R$ 5,99/100g = R$ 14,98", screen.text("quantityPreviewLabel"));
        screen.submit("quantityField");

        assertEquals(0, new BigDecimal("0.250").compareTo((BigDecimal) screen.firstRowValue("itemsTable", "quantity")));
        assertEquals(0, new BigDecimal("14.98").compareTo((BigDecimal) screen.firstRowValue("itemsTable", "totalPrice")));
    }

    @Test
    void theShortcutQuantityTimesCodeIsAlsoReadInGrams() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
        String code = screen.createProduct("Queijo fatiado", Product.Unidade.G100, "2.29");
        screen.showSaleScreen();
        screen.type("codeField", "100*" + code);
        screen.submit("codeField");

        assertEquals(0, new BigDecimal("2.29").compareTo((BigDecimal) screen.firstRowValue("itemsTable", "totalPrice")));
    }

    @Test
    void theEditScreenHidesCostAndMarginFromWhoCannotSeeThem() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR, "Ana Clara");
        grant(EnumSet.of(Permission.EDIT_PRODUCT_DATA, Permission.EDIT_PRICES));
        String code = screen.createProduct("Arroz", Product.Unidade.UN, "9.90");
        userSession.setProdutoEmEdicaoId(productService.findCode(code).getId());
        screen.showPage("edit_product");

        for (String hidden : List.of("costBox", "marginBox", "minStockBox", "profitCaption", "profitLabel",
                "priceFormationLabel")) {
            assertFalse(screen.node(hidden).isVisible(), "deve ficar oculto: " + hidden);
        }
        assertEquals("", screen.text("costField"));
        assertEquals("9.90", screen.text("priceField"));
    }

    @Test
    void theStockScreenHidesCostColumnsEvenFromAManagerWithoutThePermission() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.GERENTE, "Gerente sem custo");
        grant(EnumSet.of(Permission.EDIT_PRODUCT_DATA, Permission.EDIT_PRICES));
        screen.showPage("estoque");

        List<String> columns = visibleColumns();
        for (String hidden : List.of("Custo", "Lucro (R$)", "Lucro (%)", "Preço sugerido", "Qtd. Mín.")) {
            assertFalse(columns.contains(hidden), "coluna deve ficar oculta: " + hidden);
        }
        assertTrue(columns.contains("Vr. Venda"));
        assertFalse(screen.node("marginsToggle").isVisible());
        assertFalse(screen.node("acceptSuggestionButton").isVisible());
        assertFalse(screen.node("withoutCostBox").isVisible());
    }

    @Test
    void whoOnlyEditsPricesCanChangeThePriceColumnAndNothingElse() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR, "So preco");
        grant(EnumSet.of(Permission.EDIT_PRICES));
        screen.showPage("estoque");

        TableView<?> table = screen.node("table");
        assertTrue(table.isEditable());
        for (TableColumn<?, ?> column : table.getColumns()) {
            assertEquals("Vr. Venda".equals(column.getText()), column.isEditable(), column.getText());
        }
    }
}
