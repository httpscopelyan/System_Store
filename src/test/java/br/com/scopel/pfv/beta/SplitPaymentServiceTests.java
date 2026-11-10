package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ElectronicPaymentReceivedException;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InvalidPaymentPartException;
import br.com.scopel.pfv.beta.exception.PaymentMethodNotAvailableException;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentInput;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.SplitPaymentResult;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
import br.com.scopel.pfv.beta.service.SplitPaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SplitPaymentServiceTests {

    private static final BigDecimal INITIAL_STOCK = new BigDecimal("100.000");

    @Autowired private SplitPaymentService splitPaymentService;
    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentRepository paymentRepository;

    private User operator;
    private CashRegister register;
    private Product product;

    @BeforeEach
    void prepare() {
        User user = new User();
        user.setName("Divide");
        user.setLogin("divide_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);
        operator = userRepository.save(user);
        register = cashRegisterService.open(operator, new BigDecimal("100.00"));
    }

    private Sale saleInPayment(String unitPrice, String quantity) {
        product = new Product();
        product.setCode(UUID.randomUUID().toString().substring(0, 13));
        product.setDescription("Produto dividido");
        product.setPrice(new BigDecimal(unitPrice));
        product.setQuantity(INITIAL_STOCK);
        product.setUnit(Product.Unidade.UN);
        product.setActive(true);
        productRepository.save(product);
        Sale sale = saleService.openSale(operator, register);
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal(quantity));
        return saleService.goToPayment(sale);
    }

    private BigDecimal stockOfProduct() {
        return productRepository.findById(product.getId()).orElseThrow().getQuantity();
    }

    private List<Payment> partsOf(Sale sale) {
        return paymentRepository.findBySale_IdOrderByCreatedAtAscIdAsc(sale.getId());
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + " mas veio " + actual);
    }

    @Test
    void debitPartThenCashPartFinalizeTheSaleWhenTheTotalIsReached() {
        Sale sale = saleInPayment("90.00", "1");

        SplitPaymentResult debitPart = splitPaymentService.registerPart(sale,
                PaymentInput.card(new BigDecimal("40.00"), CardType.DEBITO));

        assertFalse(debitPart.finalized());
        assertMoney("50.00", debitPart.remainingAmount());
        assertMoney("0.00", debitPart.change());
        assertEquals(Sale.Status.EM_PAGAMENTO, debitPart.sale().getStatus());
        assertEquals(0, INITIAL_STOCK.compareTo(stockOfProduct()));

        SplitPaymentResult cashPart = splitPaymentService.registerPart(sale,
                PaymentInput.cash(new BigDecimal("50.00")));

        assertTrue(cashPart.finalized());
        assertMoney("0.00", cashPart.remainingAmount());
        assertMoney("0.00", cashPart.change());
        assertEquals(Sale.Status.FINALIZADA, cashPart.sale().getStatus());
        assertMoney("99", stockOfProduct());
        List<Payment> parts = partsOf(sale);
        assertEquals(2, parts.size());
        assertEquals(PaymentMethod.CARTAO, parts.get(0).getMethod());
        assertEquals(CardType.DEBITO, parts.get(0).getCardType());
        assertEquals(PaymentMethod.DINHEIRO, parts.get(1).getMethod());
        assertNull(parts.get(1).getCardType());
    }

    @Test
    void cashPartAboveTheRemainingStoresTheChangeOnThatPart() {
        Sale sale = saleInPayment("90.00", "1");
        splitPaymentService.registerPart(sale, PaymentInput.pix(new BigDecimal("40.00")));

        SplitPaymentResult cashPart = splitPaymentService.registerPart(sale,
                PaymentInput.cash(new BigDecimal("100.00")));

        assertTrue(cashPart.finalized());
        assertMoney("50.00", cashPart.change());
        assertMoney("100.00", cashPart.registeredPart().getAmountReceived());
        assertMoney("50.00", cashPart.registeredPart().getChange());
        assertMoney("0.00", partsOf(sale).get(0).getChange());
    }

    @Test
    void threePartsWithCentsAddUpExactlyToTheTotal() {
        Sale sale = saleInPayment("100.00", "1");

        assertFalse(splitPaymentService.registerPart(sale, PaymentInput.pix(new BigDecimal("33.33"))).finalized());
        SplitPaymentResult secondPart = splitPaymentService.registerPart(sale,
                PaymentInput.card(new BigDecimal("33.33"), CardType.CREDITO));
        assertFalse(secondPart.finalized());
        assertMoney("33.34", secondPart.remainingAmount());
        SplitPaymentResult lastPart = splitPaymentService.registerPart(sale,
                PaymentInput.card(new BigDecimal("33.34"), CardType.DEBITO));

        assertTrue(lastPart.finalized());
        assertMoney("0.00", lastPart.remainingAmount());
        assertMoney("0.00", lastPart.change());
        assertEquals(3, partsOf(sale).size());
    }

    @Test
    void oneCentShortKeepsTheSaleInPayment() {
        Sale sale = saleInPayment("100.00", "1");
        splitPaymentService.registerPart(sale, PaymentInput.pix(new BigDecimal("33.33")));
        splitPaymentService.registerPart(sale, PaymentInput.pix(new BigDecimal("33.33")));

        SplitPaymentResult shortPart = splitPaymentService.registerPart(sale,
                PaymentInput.pix(new BigDecimal("33.33")));

        assertFalse(shortPart.finalized());
        assertMoney("0.01", shortPart.remainingAmount());
        assertEquals(Sale.Status.EM_PAGAMENTO, shortPart.sale().getStatus());
        assertEquals(0, INITIAL_STOCK.compareTo(stockOfProduct()));
    }

    @Test
    void partAmountIsRoundedHalfUpToTwoDecimals() {
        Sale sale = saleInPayment("100.00", "1");

        SplitPaymentResult roundedUp = splitPaymentService.registerPart(sale,
                PaymentInput.cash(new BigDecimal("10.005")));
        SplitPaymentResult roundedDown = splitPaymentService.registerPart(sale,
                PaymentInput.cash(new BigDecimal("10.004")));

        assertMoney("10.01", roundedUp.registeredPart().getAmountReceived());
        assertMoney("10.00", roundedDown.registeredPart().getAmountReceived());
        assertMoney("79.99", roundedDown.remainingAmount());
    }

    @Test
    void zeroNegativeNullAndSubCentAmountsAreRefused() {
        Sale sale = saleInPayment("90.00", "1");

        assertThrows(InvalidPaymentPartException.class,
                () -> splitPaymentService.registerPart(sale, PaymentInput.cash(BigDecimal.ZERO)));
        assertThrows(InvalidPaymentPartException.class,
                () -> splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("-5.00"))));
        assertThrows(InvalidPaymentPartException.class,
                () -> splitPaymentService.registerPart(sale, PaymentInput.cash(null)));
        assertThrows(InvalidPaymentPartException.class,
                () -> splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("0.004"))));
        assertThrows(InvalidPaymentPartException.class, () -> splitPaymentService.registerPart(sale, null));
        assertThrows(InvalidPaymentPartException.class,
                () -> splitPaymentService.registerPart(sale, new PaymentInput(null, BigDecimal.TEN, null)));
        assertTrue(partsOf(sale).isEmpty());
    }

    @Test
    void cardPartWithoutDebitOrCreditIsRefused() {
        Sale sale = saleInPayment("90.00", "1");

        assertThrows(InvalidPaymentPartException.class,
                () -> splitPaymentService.registerPart(sale, PaymentInput.card(new BigDecimal("40.00"), null)));
        assertTrue(partsOf(sale).isEmpty());
    }

    @Test
    void creditSalePartIsRefused() {
        Sale sale = saleInPayment("90.00", "1");

        assertThrows(PaymentMethodNotAvailableException.class, () -> splitPaymentService.registerPart(sale,
                new PaymentInput(PaymentMethod.PRAZO, new BigDecimal("40.00"), null)));
        assertTrue(partsOf(sale).isEmpty());
    }

    @Test
    void cardOrPixPartAboveTheRemainingIsRefusedBecauseChangeOnlyComesFromCash() {
        Sale sale = saleInPayment("90.00", "1");
        splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("50.00")));

        assertThrows(InvalidPaymentPartException.class, () -> splitPaymentService.registerPart(sale,
                PaymentInput.card(new BigDecimal("40.01"), CardType.DEBITO)));
        assertThrows(InvalidPaymentPartException.class,
                () -> splitPaymentService.registerPart(sale, PaymentInput.pix(new BigDecimal("90.00"))));
        assertEquals(1, partsOf(sale).size());
        assertTrue(splitPaymentService.registerPart(sale,
                PaymentInput.card(new BigDecimal("40.00"), CardType.DEBITO)).finalized());
    }

    @Test
    void partIsRefusedWhenTheSaleIsNotInPayment() {
        Sale openSale = saleService.openSale(operator, register);

        assertThrows(IllegalSaleStateException.class,
                () -> splitPaymentService.registerPart(openSale, PaymentInput.cash(BigDecimal.TEN)));
        assertThrows(IllegalSaleStateException.class,
                () -> splitPaymentService.registerPart(null, PaymentInput.cash(BigDecimal.TEN)));
        assertThrows(IllegalSaleStateException.class,
                () -> splitPaymentService.registerPart(new Sale(), PaymentInput.cash(BigDecimal.TEN)));
    }

    @Test
    void finalizedSaleAcceptsNoMorePartsAndTheStockIsDecreasedOnlyOnce() {
        Sale sale = saleInPayment("30.00", "3");
        splitPaymentService.registerPart(sale, PaymentInput.pix(new BigDecimal("40.00")));
        SplitPaymentResult lastPart = splitPaymentService.registerPart(sale,
                PaymentInput.cash(new BigDecimal("50.00")));
        assertTrue(lastPart.finalized());

        assertThrows(IllegalSaleStateException.class,
                () -> splitPaymentService.registerPart(lastPart.sale(), PaymentInput.cash(BigDecimal.ONE)));
        assertThrows(IllegalSaleStateException.class,
                () -> splitPaymentService.registerPart(sale, PaymentInput.pix(BigDecimal.ONE)));

        assertMoney("97", stockOfProduct());
        assertEquals(2, partsOf(sale).size());
        assertEquals(Sale.Status.FINALIZADA, lastPart.sale().getStatus());
    }

    @Test
    void statusReportsPaidRemainingAndEveryPartNetOfChange() {
        Sale sale = saleInPayment("90.00", "1");
        SplitPaymentStatus untouched = splitPaymentService.status(sale);
        assertFalse(untouched.hasParts());
        assertMoney("90.00", untouched.remainingAmount());

        splitPaymentService.registerPart(sale, PaymentInput.card(new BigDecimal("40.00"), CardType.CREDITO));
        SplitPaymentStatus partial = splitPaymentService.status(sale);

        assertTrue(partial.hasParts());
        assertTrue(splitPaymentService.hasRegisteredParts(sale));
        assertMoney("90.00", partial.total());
        assertMoney("40.00", partial.paidAmount());
        assertMoney("50.00", partial.remainingAmount());
        assertEquals(1, partial.parts().size());

        splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("60.00")));
        SplitPaymentStatus paid = splitPaymentService.status(sale);
        assertMoney("90.00", paid.paidAmount());
        assertMoney("0.00", paid.remainingAmount());
    }

    @Test
    void cashPartCanBeRemovedBeforeTheSaleIsFinalized() {
        Sale sale = saleInPayment("90.00", "1");
        Payment cashPart = splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("50.00")))
                .registeredPart();

        SplitPaymentStatus afterRemoval = splitPaymentService.removeCashPart(sale, cashPart.getId());

        assertFalse(afterRemoval.hasParts());
        assertMoney("90.00", afterRemoval.remainingAmount());
        assertTrue(partsOf(sale).isEmpty());
    }

    @Test
    void cardOrPixPartCanNeverBeRemoved() {
        Sale sale = saleInPayment("90.00", "1");
        Payment debitPart = splitPaymentService.registerPart(sale,
                PaymentInput.card(new BigDecimal("40.00"), CardType.DEBITO)).registeredPart();
        Payment pixPart = splitPaymentService.registerPart(sale, PaymentInput.pix(new BigDecimal("10.00")))
                .registeredPart();

        assertThrows(ElectronicPaymentReceivedException.class,
                () -> splitPaymentService.removeCashPart(sale, debitPart.getId()));
        assertThrows(ElectronicPaymentReceivedException.class,
                () -> splitPaymentService.removeCashPart(sale, pixPart.getId()));
        assertEquals(2, partsOf(sale).size());
    }

    @Test
    void removingAnUnknownPartOrAPartOfAnotherSaleIsRefused() {
        Sale sale = saleInPayment("90.00", "1");
        Sale otherSale = saleInPayment("20.00", "1");
        Payment otherCashPart = splitPaymentService.registerPart(otherSale,
                PaymentInput.cash(new BigDecimal("5.00"))).registeredPart();

        assertThrows(InvalidPaymentPartException.class,
                () -> splitPaymentService.removeCashPart(sale, otherCashPart.getId()));
        assertThrows(InvalidPaymentPartException.class, () -> splitPaymentService.removeCashPart(sale, -1L));
        assertThrows(InvalidPaymentPartException.class, () -> splitPaymentService.removeCashPart(sale, null));
        assertEquals(1, partsOf(otherSale).size());
    }

    @Test
    void partOfAFinalizedSaleCanNotBeRemoved() {
        Sale sale = saleInPayment("90.00", "1");
        Payment cashPart = splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("90.00")))
                .registeredPart();

        assertThrows(IllegalSaleStateException.class,
                () -> splitPaymentService.removeCashPart(sale, cashPart.getId()));
        assertEquals(1, partsOf(sale).size());
    }

    @Test
    void pendingSaleIsFoundAgainAfterARestartWithItsItems() {
        Sale sale = saleInPayment("90.00", "1");
        assertTrue(splitPaymentService.findSaleWithPendingParts(operator).isEmpty());

        splitPaymentService.registerPart(sale, PaymentInput.card(new BigDecimal("40.00"), CardType.DEBITO));

        Sale recovered = splitPaymentService.findSaleWithPendingParts(operator).orElseThrow();
        assertEquals(sale.getId(), recovered.getId());
        assertEquals(1, recovered.getItems().size());
        assertTrue(splitPaymentService.findSaleWithPendingParts(null).isEmpty());

        splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("50.00")));
        assertTrue(splitPaymentService.findSaleWithPendingParts(operator).isEmpty());
    }
}
