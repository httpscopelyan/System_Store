package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import javafx.scene.input.KeyCode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class SaleScreenFlowTests {

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
    void prepare() throws Exception {
        screen = SaleScreenHarness.open(context, User.Role.OPERADOR);
    }

    @AfterEach
    void cleanUp() throws Exception {
        screen.close();
    }

    private void scan(String text) throws Exception {
        screen.type("codeField", text);
        screen.submit("codeField");
    }

    @Test
    void scanningAnExactCodeMovesTheFocusToTheQuantityFieldWithOne_RF57() throws Exception {
        String code = screen.createProduct("Refrigerante lata", Product.Unidade.UN, "4.50");
        screen.showSaleScreen();
        scan(code);
        assertFalse(screen.isDisabled("quantityField"));
        assertSame(screen.node("quantityField"), screen.focusOwner());
        assertEquals("1", screen.text("quantityField"));
        assertEquals(0, screen.tableRows("itemsTable"));
    }

    @Test
    void confirmingTheQuantityAddsExactlyThatQuantityToTheSale_RF57() throws Exception {
        String code = screen.createProduct("Refrigerante lata", Product.Unidade.UN, "4.50");
        screen.showSaleScreen();
        scan(code);
        screen.type("quantityField", "5");
        screen.submit("quantityField");
        assertEquals(1, screen.tableRows("itemsTable"));
        assertEquals(0, new BigDecimal("5").compareTo((BigDecimal) screen.firstRowValue("itemsTable", "quantity")));
        assertEquals("", screen.text("codeField"));
        assertTrue(screen.isDisabled("quantityField"));
        assertSame(screen.node("codeField"), screen.focusOwner());
    }

    @Test
    void escapeOnTheQuantityFieldCancelsWithoutAddingAnything_RF57() throws Exception {
        String code = screen.createProduct("Refrigerante lata", Product.Unidade.UN, "4.50");
        screen.showSaleScreen();
        scan(code);
        screen.press("quantityField", KeyCode.ESCAPE);
        assertEquals(0, screen.tableRows("itemsTable"));
        assertTrue(screen.isDisabled("quantityField"));
        assertSame(screen.node("codeField"), screen.focusOwner());
    }

    @Test
    void legacyQuantityTimesCodeStillAddsWithoutTheQuantityStep_RN05() throws Exception {
        String code = screen.createProduct("Refrigerante lata", Product.Unidade.UN, "4.50");
        screen.showSaleScreen();
        scan("3*" + code);
        assertEquals(1, screen.tableRows("itemsTable"));
        assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) screen.firstRowValue("itemsTable", "quantity")));
        assertTrue(screen.isDisabled("quantityField"));
    }

    @Test
    void weightProductSwitchesTheQuantityFieldToKilograms_RF60() throws Exception {
        String code = screen.createProduct("Alcatra", Product.Unidade.KG, "39.90");
        screen.showSaleScreen();
        scan(code);
        assertEquals("Peso (KG)", screen.text("quantityLabel"));
        assertEquals("", screen.text("quantityField"));
        screen.type("quantityField", "0,750");
        assertEquals("0,75 kg × R$ 39,90 = R$ 29,93", screen.text("quantityPreviewLabel"));
        screen.submit("quantityField");
        assertEquals(0, new BigDecimal("0.750").compareTo((BigDecimal) screen.firstRowValue("itemsTable", "quantity")));
        assertEquals(0, new BigDecimal("29.93").compareTo((BigDecimal) screen.firstRowValue("itemsTable", "totalPrice")));
    }

    @Test
    void unitProductRefusesDecimalQuantityTyping_RN05() throws Exception {
        String code = screen.createProduct("Refrigerante lata", Product.Unidade.UN, "4.50");
        screen.showSaleScreen();
        scan(code);
        screen.type("quantityField", "1,5");
        assertEquals("1", screen.text("quantityField"));
    }

    @Test
    void quantityAboveTheLimitIsRefusedAndKeepsTheProductPending_RN24() throws Exception {
        String code = screen.createProduct("Refrigerante lata", Product.Unidade.UN, "4.50");
        screen.showSaleScreen();
        scan(code);
        screen.type("quantityField", "1000");
        screen.submit("quantityField");
        assertEquals("Quantidade acima do limite — confirme", screen.text("statusLabel"));
        assertEquals(0, screen.tableRows("itemsTable"));
        assertFalse(screen.isDisabled("quantityField"));
    }

    @Test
    void textSearchWithASingleMatchGoesStraightToTheQuantityStep_RF58() throws Exception {
        String code = screen.createProduct("Zzxq Biscoito recheado", Product.Unidade.UN, "3.00");
        screen.showSaleScreen();
        scan("zzxq biscoito");
        assertFalse(screen.isDisabled("quantityField"));
        assertEquals(code, screen.text("codeField"));
    }

    @Test
    void textSearchWithSeveralMatchesOpensTheSuggestionList_RF58() throws Exception {
        screen.createProduct("Qqzw Suco de uva", Product.Unidade.UN, "6.00");
        screen.createProduct("Qqzw Suco de laranja", Product.Unidade.UN, "6.50");
        screen.showSaleScreen();
        scan("qqzw");
        assertTrue(screen.anyPopupShowing());
        assertTrue(screen.isDisabled("quantityField"));
    }

    @Test
    void arrowDownAndEnterPickAListedProductAndOpenTheQuantityStep_RF59() throws Exception {
        screen.createProduct("Qqzw Suco de uva", Product.Unidade.UN, "6.00");
        screen.createProduct("Qqzw Suco de laranja", Product.Unidade.UN, "6.50");
        screen.showSaleScreen();
        screen.focus("codeField");
        screen.type("codeField", "qqzw");
        screen.pause(600);
        assertTrue(screen.anyPopupShowing());
        screen.press("codeField", KeyCode.DOWN);
        screen.press("codeField", KeyCode.ENTER);
        assertFalse(screen.isDisabled("quantityField"));
        assertFalse(screen.anyPopupShowing());
        assertEquals(0, screen.tableRows("itemsTable"));
    }

    @Test
    void escapeClosesTheSuggestionListKeepingTheTypedText_RF59() throws Exception {
        screen.createProduct("Qqzw Suco de uva", Product.Unidade.UN, "6.00");
        screen.createProduct("Qqzw Suco de laranja", Product.Unidade.UN, "6.50");
        screen.showSaleScreen();
        screen.focus("codeField");
        screen.type("codeField", "qqzw");
        screen.pause(600);
        assertTrue(screen.anyPopupShowing());
        screen.press("codeField", KeyCode.ESCAPE);
        assertFalse(screen.anyPopupShowing());
        assertEquals("qqzw", screen.text("codeField"));
    }

    @Test
    void codePrefixListsCodesWithTheirNames_RF59() throws Exception {
        String first = screen.createProduct("Qqzw Pao de forma", Product.Unidade.UN, "7.00");
        screen.createProduct("Qqzw Pao integral", Product.Unidade.UN, "8.00");
        screen.showSaleScreen();
        screen.focus("codeField");
        screen.type("codeField", first.substring(0, 5));
        screen.pause(600);
        assertTrue(screen.anyPopupShowing());
    }
}
