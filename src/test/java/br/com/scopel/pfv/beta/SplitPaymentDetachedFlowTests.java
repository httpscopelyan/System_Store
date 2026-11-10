package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.ElectronicPaymentReceivedException;
import br.com.scopel.pfv.beta.exception.SplitPaymentInProgressException;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.PaymentInput;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.SplitPaymentResult;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
import br.com.scopel.pfv.beta.service.cash.CashClosingService;
import br.com.scopel.pfv.beta.service.cash.CashSummary;
import br.com.scopel.pfv.beta.service.cash.CashSummaryService;
import br.com.scopel.pfv.beta.service.cash.ClosingCount;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class SplitPaymentDetachedFlowTests {

    @Autowired private ApplicationContext context;
    @Autowired private UserSession userSession;
    @Autowired private SaleService saleService;
    @Autowired private SplitPaymentService splitPaymentService;
    @Autowired private CashSummaryService cashSummaryService;
    @Autowired private CashClosingService cashClosingService;
    @Autowired private ProductRepository productRepository;

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
        harness = SaleScreenHarness.open(context, User.Role.OPERADOR);
        productCode = harness.createProduct("Produto dividido", Product.Unidade.UN, "30.00");
    }

    @AfterEach
    void cleanUp() throws Exception {
        harness.close();
    }

    private Sale saleOfNinetyInPayment() {
        Sale sale = saleService.openSale(userSession.getLoggedUser(), userSession.getCaixaAtual());
        sale = saleService.addItem(sale, productCode, new BigDecimal("3"));
        return saleService.goToPayment(sale);
    }

    private BigDecimal stockOfProduct() {
        return productRepository.findByCodeIgnoreCase(productCode).orElseThrow().getQuantity();
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + " mas veio " + actual);
    }

    @Test
    void saleReturnedAfterAPartialPartKeepsItsItemsReadableOutsideTheTransaction() {
        SplitPaymentResult result = splitPaymentService.registerPart(saleOfNinetyInPayment(),
                PaymentInput.cash(new BigDecimal("50.00")));

        assertFalse(result.finalized());
        assertEquals(1, result.sale().getItems().size());
        assertMoney("40.00", result.remainingAmount());
    }

    @Test
    void cashOnlyPartsAreDiscardedWhenTheSaleReturnsToEditingAndTheItemsStayReadable() {
        SplitPaymentResult result = splitPaymentService.registerPart(saleOfNinetyInPayment(),
                PaymentInput.cash(new BigDecimal("50.00")));

        Sale reopened = saleService.reopenForEditing(result.sale());

        assertEquals(Sale.Status.ABERTA, reopened.getStatus());
        assertEquals(1, reopened.getItems().size());
        assertFalse(splitPaymentService.hasRegisteredParts(reopened));
    }

    @Test
    void fortyOnDebitPlusFiftyInCashFinalizesAndReachesTheCashSummaryInEachForm() {
        BigDecimal stockBefore = stockOfProduct();
        SplitPaymentResult debitPart = splitPaymentService.registerPart(saleOfNinetyInPayment(),
                PaymentInput.card(new BigDecimal("40.00"), CardType.DEBITO));
        SplitPaymentResult cashPart = splitPaymentService.registerPart(debitPart.sale(),
                PaymentInput.cash(new BigDecimal("60.00")));

        assertTrue(cashPart.finalized());
        assertMoney("10.00", cashPart.change());
        assertEquals(1, cashPart.sale().getItems().size());
        assertEquals(0, stockBefore.subtract(new BigDecimal("3")).compareTo(stockOfProduct()));
        assertEquals(2, splitPaymentService.receiptPayments(cashPart.sale()).size());

        CashSummary summary = cashSummaryService.overview(userSession.getCaixaAtual()).summary();
        assertMoney("40.00", summary.debit());
        assertMoney("50.00", summary.cashIn());
        assertMoney("0.00", summary.credit());
        assertMoney("0.00", summary.pix());
    }

    @Test
    void receivedCardPartBlocksReopeningCancellingAndClosingTheCashRegister() {
        SplitPaymentResult debitPart = splitPaymentService.registerPart(saleOfNinetyInPayment(),
                PaymentInput.card(new BigDecimal("40.00"), CardType.DEBITO));
        Sale saleWithCardPart = debitPart.sale();
        BigDecimal zero = BigDecimal.ZERO;

        assertThrows(ElectronicPaymentReceivedException.class, () -> saleService.reopenForEditing(saleWithCardPart));
        assertThrows(ElectronicPaymentReceivedException.class, () -> saleService.cancelSale(saleWithCardPart));
        assertThrows(SplitPaymentInProgressException.class, () -> cashClosingService.close(
                userSession.getCaixaAtual().getId(), new ClosingCount(new BigDecimal("50.00"), zero, zero, zero, zero, null)));
        assertThrows(SplitPaymentInProgressException.class, () -> saleService.payPix(saleWithCardPart));
    }
}
