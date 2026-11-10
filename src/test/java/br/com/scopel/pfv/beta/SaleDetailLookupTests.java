package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.SaleHistoryEntry;
import br.com.scopel.pfv.beta.service.SaleHistoryService;
import br.com.scopel.pfv.beta.service.SaleService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class SaleDetailLookupTests {

    @Autowired private ApplicationContext context;
    @Autowired private UserSession userSession;
    @Autowired private SaleService saleService;
    @Autowired private SaleHistoryService saleHistoryService;

    private SaleScreenHarness harness;
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
        harness = SaleScreenHarness.open(context, User.Role.OPERADOR, "Operador do detalhe");
        productCode = harness.createProduct("Arroz detalhe", Product.Unidade.UN, "12.50");
    }

    @AfterEach
    void cleanUp() throws Exception {
        harness.close();
    }

    private Sale finalizedCashSale(String quantity) {
        Sale sale = saleService.openSale(userSession.getLoggedUser(), userSession.getCaixaAtual());
        sale = saleService.addItem(sale, productCode, new BigDecimal(quantity));
        sale = saleService.goToPayment(sale);
        return saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal("100.00"));
    }

    @Test
    void finalizedSaleDetailListsItemsPaymentsAndTheOperatorOutsideAnyTransaction() {
        Sale finalizedSale = finalizedCashSale("3");

        SaleHistoryEntry detail = saleHistoryService.findSaleDetail(finalizedSale.getId()).orElseThrow();

        assertEquals("Operador do detalhe", detail.operatorName());
        assertEquals(1, detail.items().size());
        assertEquals("Arroz detalhe", detail.items().get(0).productDescription());
        assertEquals(0, new BigDecimal("3").compareTo(detail.items().get(0).quantity()));
        assertEquals(0, new BigDecimal("12.50").compareTo(detail.items().get(0).unitPrice()));
        assertEquals(0, new BigDecimal("37.50").compareTo(detail.items().get(0).subtotal()));
        assertEquals(1, detail.payments().size());
        assertEquals(PaymentMethod.DINHEIRO, detail.payments().get(0).method());
        assertEquals(0, new BigDecimal("62.50").compareTo(detail.payments().get(0).change()));
    }

    @Test
    void unknownOrNullSaleIdHasNoDetail() {
        assertTrue(saleHistoryService.findSaleDetail(null).isEmpty());
        assertTrue(saleHistoryService.findSaleDetail(-1L).isEmpty());
    }

    @Test
    void detailOfAnOpenSaleStillListsItsItems() {
        Sale openSale = saleService.openSale(userSession.getLoggedUser(), userSession.getCaixaAtual());
        saleService.addItem(openSale, productCode, new BigDecimal("2"));

        Optional<SaleHistoryEntry> detail = saleHistoryService.findSaleDetail(openSale.getId());

        assertEquals(1, detail.orElseThrow().items().size());
    }
}
