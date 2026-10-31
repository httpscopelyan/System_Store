package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class PriceCheckScreenTests {

    @Autowired private ApplicationContext context;

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

    @Test
    void typingPartOfTheNameListsEveryFlavourWithPriceAndQuantity_RF75() throws Exception {
        screen.createProduct("Zqk Red Bull Tropical", Product.Unidade.UN, "9.90");
        screen.createProduct("Zqk Red Bull Original", Product.Unidade.UN, "9.50");
        screen.createProduct("Zqk Monster Verde", Product.Unidade.UN, "8.00");
        screen.showPage("price_check");
        screen.type("nameField", "zqk red");
        screen.pause(600);
        assertEquals(2, screen.tableRows("resultsTable"));
        assertTrue(screen.text("statusLabel").startsWith("2 produto"));
    }

    @Test
    void onlyTheInitialsAlreadyListMatchingProducts_RF75() throws Exception {
        screen.createProduct("Zqk Red Bull Tropical", Product.Unidade.UN, "9.90");
        screen.createProduct("Zqk Monster Verde", Product.Unidade.UN, "8.00");
        screen.showPage("price_check");
        screen.type("nameField", "zq");
        screen.pause(600);
        assertEquals(2, screen.tableRows("resultsTable"));
    }

    @Test
    void codeSearchFindsTheProduct_RF75() throws Exception {
        String code = screen.createProduct("Zqk Pao Frances", Product.Unidade.UN, "0.90");
        screen.showPage("price_check");
        screen.type("codeField", code);
        screen.pause(600);
        assertEquals(1, screen.tableRows("resultsTable"));
    }

    @Test
    void unknownTermSaysNotFound_RF75() throws Exception {
        screen.showPage("price_check");
        screen.type("nameField", "naoexiste987xyz");
        screen.pause(600);
        assertEquals(0, screen.tableRows("resultsTable"));
        assertEquals("Produto não encontrado", screen.text("statusLabel"));
    }
}
