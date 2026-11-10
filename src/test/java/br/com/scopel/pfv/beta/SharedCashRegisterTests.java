package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.CashRegisterAlreadyOpenException;
import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.CashRegisterRepository;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.cash.CashClosingService;
import br.com.scopel.pfv.beta.service.cash.CashMovementRow;
import br.com.scopel.pfv.beta.service.cash.CashOverview;
import br.com.scopel.pfv.beta.service.cash.CashSummaryService;
import br.com.scopel.pfv.beta.service.cash.ClosingCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SharedCashRegisterTests {

    private static final String SHARED_REGISTER_OPERATORS_SQL =
            "(select id from users where lower(name) in ('dayana', 'ana clara')"
                    + " or lower(login) in ('dayana', 'ana clara'))";

    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private CashSummaryService cashSummaryService;
    @Autowired private CashClosingService cashClosingService;
    @Autowired private SaleService saleService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CashRegisterRepository cashRegisterRepository;
    @Autowired private JdbcTemplate jdbc;

    private User dayana;
    private User anaClara;
    private Product product;

    @BeforeEach
    void prepare() {
        jdbc.update("update cash_register set status = 'CLOSED' where status = 'OPEN' and operator_id in "
                + SHARED_REGISTER_OPERATORS_SQL);
        dayana = persistedUser("Dayana");
        anaClara = persistedUser("Ana Clara");
        product = new Product();
        product.setCode(UUID.randomUUID().toString().substring(0, 13));
        product.setDescription("Produto do caixa unico");
        product.setPrice(new BigDecimal("10.00"));
        product.setQuantity(new BigDecimal("500.000"));
        product.setUnit(Product.Unidade.UN);
        product.setActive(true);
        productRepository.save(product);
    }

    private User persistedUser(String name) {
        User user = new User();
        user.setName(name);
        user.setLogin("shared_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);
        return userRepository.save(user);
    }

    private Sale finalizedCashSale(User seller, CashRegister register, String quantity) {
        Sale sale = saleService.openSale(seller, register);
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal(quantity));
        sale = saleService.goToPayment(sale);
        return saleService.pay(sale, PaymentMethod.DINHEIRO, sale.getTotal());
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + " mas veio " + actual);
    }

    @Test
    void anaClaraFindsTheRegisterThatDayanaOpened() {
        CashRegister sharedRegister = cashRegisterService.open(dayana, new BigDecimal("100.00"));

        assertEquals(sharedRegister.getId(), cashRegisterService.findOpenByOperator(anaClara).orElseThrow().getId());
        assertEquals(sharedRegister.getId(), cashRegisterService.findOpenByOperator(dayana).orElseThrow().getId());
    }

    @Test
    void anaClaraCannotOpenASecondRegisterWhileTheSharedOneIsOpen() {
        cashRegisterService.open(dayana, new BigDecimal("100.00"));

        assertThrows(CashRegisterAlreadyOpenException.class,
                () -> cashRegisterService.open(anaClara, new BigDecimal("50.00")));
    }

    @Test
    void salesOfBothOperatorsLandInTheSharedRegisterAndShowTheirOperatorNames() {
        CashRegister sharedRegister = cashRegisterService.open(dayana, new BigDecimal("100.00"));
        CashRegister registerSeenByAnaClara = cashRegisterService.findOpenByOperator(anaClara).orElseThrow();

        finalizedCashSale(dayana, sharedRegister, "1");
        finalizedCashSale(anaClara, registerSeenByAnaClara, "2");

        CashOverview overview = cashSummaryService.overview(sharedRegister);
        assertMoney("30.00", overview.summary().cashIn());
        assertMoney("130.00", overview.summary().finalBalance());
        List<String> operatorNames = overview.rows().stream().map(CashMovementRow::operatorName).toList();
        assertTrue(operatorNames.contains("Dayana"));
        assertTrue(operatorNames.contains("Ana Clara"));
    }

    @Test
    void eitherOperatorClosesTheSharedRegisterAndTheClosingDifferenceCoversBothSales() {
        CashRegister sharedRegister = cashRegisterService.open(dayana, new BigDecimal("100.00"));
        finalizedCashSale(dayana, sharedRegister, "1");
        finalizedCashSale(anaClara, sharedRegister, "2");
        CashRegister registerSeenByAnaClara = cashRegisterService.findOpenByOperator(anaClara).orElseThrow();
        BigDecimal zero = BigDecimal.ZERO;

        CashRegister closed = cashClosingService.close(registerSeenByAnaClara.getId(),
                new ClosingCount(new BigDecimal("130.00"), zero, zero, zero, zero, null));

        assertEquals(CashRegister.Status.CLOSED, closed.getStatus());
        assertMoney("0.00", closed.getDifference());
        assertTrue(cashRegisterService.findOpenByOperator(dayana).isEmpty());
        assertTrue(cashRegisterService.findOpenByOperator(anaClara).isEmpty());
    }

    @Test
    void historyOfEitherOperatorListsTheSharedRegister() {
        CashRegister sharedRegister = cashRegisterService.open(dayana, new BigDecimal("100.00"));

        assertTrue(cashRegisterService.history(anaClara).stream()
                .anyMatch(register -> register.getId().equals(sharedRegister.getId())));
        assertTrue(cashRegisterService.history(dayana).stream()
                .anyMatch(register -> register.getId().equals(sharedRegister.getId())));
    }

    @Test
    void anOperatorOutsideTheGroupKeepsItsOwnSeparateRegister() {
        CashRegister sharedRegister = cashRegisterService.open(dayana, new BigDecimal("100.00"));
        User outsider = persistedUser("Maria");

        assertTrue(cashRegisterService.findOpenByOperator(outsider).isEmpty());
        CashRegister outsiderRegister = cashRegisterService.open(outsider, new BigDecimal("20.00"));

        assertNotEquals(sharedRegister.getId(), outsiderRegister.getId());
        assertEquals(sharedRegister.getId(), cashRegisterService.findOpenByOperator(anaClara).orElseThrow().getId());
        assertEquals(outsiderRegister.getId(), cashRegisterService.findOpenByOperator(outsider).orElseThrow().getId());
    }

    @Test
    void withdrawalStoresTheOperatorWhoMadeItAndShowsTheNameInTheMovementRow() {
        CashRegister sharedRegister = cashRegisterService.open(dayana, new BigDecimal("100.00"));

        CashMovement withdrawal = cashRegisterService.withdrawal(sharedRegister.getId(), new BigDecimal("30.00"),
                "Pagamento a fornecedor", anaClara);

        assertEquals(anaClara.getId(), withdrawal.getOperator().getId());
        CashMovementRow movementRow = cashSummaryService.overview(sharedRegister).rows().stream()
                .filter(row -> row.movement() != null).findFirst().orElseThrow();
        assertEquals("Ana Clara", movementRow.operatorName());
    }

    @Test
    void supplementWithoutAnOperatorIsRefused() {
        CashRegister sharedRegister = cashRegisterService.open(dayana, new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> cashRegisterService.supplement(sharedRegister.getId(), new BigDecimal("10"), "reforço", null));
        assertThrows(IllegalArgumentException.class,
                () -> cashRegisterService.withdrawal(sharedRegister.getId(), new BigDecimal("10"), "retirada", null));
    }

    @Test
    void whenTwoRegistersWereOpenBeforeTheSharingTheOlderOneIsFoundFirstSoNoneIsLeftOrphaned() {
        CashRegister olderRegister = cashRegisterService.open(dayana, new BigDecimal("100.00"));
        CashRegister newerRegister = new CashRegister();
        newerRegister.setOperator(anaClara);
        newerRegister.setStatus(CashRegister.Status.OPEN);
        newerRegister.setOpeningAmount(new BigDecimal("40.00"));
        newerRegister.setOpenedAt(LocalDateTime.now().plusMinutes(5));
        cashRegisterRepository.save(newerRegister);
        BigDecimal zero = BigDecimal.ZERO;

        assertEquals(olderRegister.getId(), cashRegisterService.findOpenByOperator(anaClara).orElseThrow().getId());
        cashClosingService.close(olderRegister.getId(),
                new ClosingCount(new BigDecimal("100.00"), zero, zero, zero, zero, null));

        assertFalse(cashRegisterService.findOpenByOperator(dayana).isEmpty());
        assertEquals(newerRegister.getId(), cashRegisterService.findOpenByOperator(dayana).orElseThrow().getId());
    }

    @Test
    void movementOfALegacyRowWithoutOperatorShowsAnEmptyOperatorName() {
        CashRegister sharedRegister = cashRegisterService.open(dayana, new BigDecimal("100.00"));
        CashMovement withdrawal = cashRegisterService.withdrawal(sharedRegister.getId(), new BigDecimal("10.00"),
                "Troco antigo", dayana);
        jdbc.update("update cash_movement set operator_id = null where id = ?", withdrawal.getId());
        org.springframework.test.util.ReflectionTestUtils.setField(withdrawal, "operator", null);

        CashMovementRow legacyRow = cashSummaryService.overview(sharedRegister).rows().stream()
                .filter(row -> row.movement() != null).findFirst().orElseThrow();

        assertEquals("", legacyRow.operatorName());
    }
}
