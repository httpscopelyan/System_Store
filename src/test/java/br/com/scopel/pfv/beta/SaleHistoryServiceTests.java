package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.PaymentInput;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.SaleHistoryEntry;
import br.com.scopel.pfv.beta.service.SaleHistoryService;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SaleHistoryServiceTests {

    @Autowired private SaleHistoryService saleHistoryService;
    @Autowired private SplitPaymentService splitPaymentService;
    @Autowired private SaleService saleService;
    @Autowired private ProductService productService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private UserRepository userRepository;

    private User operator;
    private User otherOperator;
    private CashRegister register;
    private CashRegister otherRegister;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    private User persistedOperator(String name) {
        User user = new User();
        user.setName(name);
        user.setLogin("hist_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);
        return userRepository.save(user);
    }

    @BeforeEach
    void prepare() {
        operator = persistedOperator("Operadora vendas");
        otherOperator = persistedOperator("Outro operador");
        register = cashRegisterService.open(operator, new BigDecimal("100.00"));
        otherRegister = cashRegisterService.open(otherOperator, new BigDecimal("100.00"));
        periodStart = LocalDateTime.now().minusMinutes(5);
        periodEnd = LocalDateTime.now().plusMinutes(5);
    }

    private Sale saleInPayment(User seller, CashRegister sellerRegister, String description, String price,
                               String quantity) {
        String code = "his " + UUID.randomUUID().toString().substring(0, 8);
        productService.create(code, description, Product.Unidade.UN, new BigDecimal(price), BigDecimal.ONE,
                new BigDecimal("100"), BigDecimal.ZERO, null, true);
        Sale sale = saleService.openSale(seller, sellerRegister);
        sale = saleService.addItem(sale, code, new BigDecimal(quantity));
        return saleService.goToPayment(sale);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + " mas veio " + actual);
    }

    @Test
    void finalizedSaleComesWithEveryItemAndEveryPayment() {
        Sale sale = saleInPayment(operator, register, "Arroz 5kg", "30.00", "3");
        splitPaymentService.registerPart(sale, PaymentInput.card(new BigDecimal("40.00"), CardType.DEBITO));
        splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("100.00")));

        Page<SaleHistoryEntry> history = saleHistoryService.finalizedSales(operator.getId(), periodStart, periodEnd,
                0, 10);

        assertEquals(1, history.getTotalElements());
        SaleHistoryEntry entry = history.getContent().get(0);
        assertEquals(sale.getId(), entry.saleId());
        assertEquals(operator.getId(), entry.operatorId());
        assertEquals("Operadora vendas", entry.operatorName());
        assertNull(entry.customerName());
        assertMoney("90.00", entry.total());
        assertEquals(1, entry.items().size());
        assertEquals("Arroz 5kg", entry.items().get(0).productDescription());
        assertMoney("3", entry.items().get(0).quantity());
        assertMoney("30.00", entry.items().get(0).unitPrice());
        assertMoney("90.00", entry.items().get(0).subtotal());
        assertEquals(2, entry.payments().size());
        assertEquals(PaymentMethod.CARTAO, entry.payments().get(0).method());
        assertEquals(CardType.DEBITO, entry.payments().get(0).cardType());
        assertMoney("40.00", entry.payments().get(0).netAmount());
        assertEquals(PaymentMethod.DINHEIRO, entry.payments().get(1).method());
        assertMoney("100.00", entry.payments().get(1).amountReceived());
        assertMoney("50.00", entry.payments().get(1).change());
        assertMoney("50.00", entry.payments().get(1).netAmount());
    }

    @Test
    void historyIsFilteredByOperatorAndListsTheNewestSaleFirst() {
        Sale firstSale = saleService.payPix(saleInPayment(operator, register, "Feijão", "8.00", "1"));
        Sale secondSale = saleService.payCard(saleInPayment(operator, register, "Café", "15.00", "1"),
                CardType.CREDITO);
        Sale saleOfOther = saleService.payPix(saleInPayment(otherOperator, otherRegister, "Leite", "5.00", "1"));

        Page<SaleHistoryEntry> ofOperator = saleHistoryService.finalizedSales(operator.getId(), periodStart,
                periodEnd, 0, 10);
        Page<SaleHistoryEntry> ofOther = saleHistoryService.finalizedSales(otherOperator.getId(), periodStart,
                periodEnd, 0, 10);
        Page<SaleHistoryEntry> ofEveryone = saleHistoryService.finalizedSales(null, periodStart, periodEnd, 0, 200);

        assertEquals(2, ofOperator.getTotalElements());
        assertEquals(secondSale.getId(), ofOperator.getContent().get(0).saleId());
        assertEquals(firstSale.getId(), ofOperator.getContent().get(1).saleId());
        assertEquals(1, ofOther.getTotalElements());
        assertEquals(saleOfOther.getId(), ofOther.getContent().get(0).saleId());
        assertTrue(ofEveryone.getTotalElements() >= 3);
    }

    @Test
    void unfinishedCanceledAndOutOfPeriodSalesStayOut() {
        saleService.payPix(saleInPayment(operator, register, "Feijão", "8.00", "1"));
        Sale halfPaid = saleInPayment(operator, register, "Café", "15.00", "1");
        splitPaymentService.registerPart(halfPaid, PaymentInput.cash(new BigDecimal("5.00")));
        saleService.cancelSale(saleInPayment(operator, register, "Leite", "5.00", "1"));

        assertEquals(1, saleHistoryService.finalizedSales(operator.getId(), periodStart, periodEnd, 0, 10)
                .getTotalElements());
        assertEquals(0, saleHistoryService.finalizedSales(operator.getId(), periodStart.minusDays(2),
                periodStart.minusDays(1), 0, 10).getTotalElements());
    }

    @Test
    void historyIsPaged() {
        for (int saleNumber = 1; saleNumber <= 3; saleNumber++) {
            saleService.payPix(saleInPayment(operator, register, "Item " + saleNumber, "2.00", "1"));
        }

        Page<SaleHistoryEntry> firstPage = saleHistoryService.finalizedSales(operator.getId(), periodStart,
                periodEnd, 0, 2);
        Page<SaleHistoryEntry> secondPage = saleHistoryService.finalizedSales(operator.getId(), periodStart,
                periodEnd, 1, 2);

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getContent().size());
        assertEquals(1, secondPage.getContent().size());
        assertEquals("Item 3", firstPage.getContent().get(0).items().get(0).productDescription());
        assertEquals("Item 1", secondPage.getContent().get(0).items().get(0).productDescription());
    }

    @Test
    void invalidPeriodOrPageIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> saleHistoryService.finalizedSales(operator.getId(), periodEnd, periodStart, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> saleHistoryService.finalizedSales(operator.getId(), null, periodEnd, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> saleHistoryService.finalizedSales(operator.getId(), periodStart, periodEnd, -1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> saleHistoryService.finalizedSales(operator.getId(), periodStart, periodEnd, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> saleHistoryService.finalizedSales(operator.getId(),
                periodStart, periodEnd, 0, SaleHistoryService.MAX_PAGE_SIZE + 1));
    }
}
