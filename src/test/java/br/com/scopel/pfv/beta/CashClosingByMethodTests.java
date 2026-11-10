package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.CashRegisterClosedException;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.cash.CashClosingService;
import br.com.scopel.pfv.beta.service.cash.CashReopeningService;
import br.com.scopel.pfv.beta.service.cash.CashSummaryService;
import br.com.scopel.pfv.beta.service.cash.ClosingCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class CashClosingByMethodTests {

    @Autowired private CashClosingService closingService;
    @Autowired private CashReopeningService reopeningService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private CashSummaryService cashSummaryService;
    @Autowired private ReceiptService receiptService;
    @Autowired private SaleService saleService;
    @Autowired private ProductService productService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private User operator;
    private CashRegister register;

    @BeforeEach
    void prepare() {
        User user = new User();
        user.setName("Fecha");
        user.setLogin("fecha_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);
        operator = userRepository.save(user);
        register = cashRegisterService.open(operator, new BigDecimal("100.00"));
        sell("10.00", PaymentMethod.DINHEIRO, null, "10.00");
        sell("20.00", PaymentMethod.PIX, null, null);
        sell("30.00", PaymentMethod.CARTAO, CardType.CREDITO, null);
        sell("40.00", PaymentMethod.CARTAO, CardType.DEBITO, null);
        cashRegisterService.withdrawal(register.getId(), new BigDecimal("5.00"), "Pagamento a fornecedor", register.getOperator());
    }

    private void sell(String price, PaymentMethod method, CardType cardType, String received) {
        String code = "fch " + UUID.randomUUID().toString().substring(0, 8);
        productService.create(code, "Item " + code, Product.Unidade.UN, new BigDecimal(price), BigDecimal.ONE,
                new BigDecimal("100"), BigDecimal.ZERO, null, true);
        Sale sale = saleService.openSale(operator, register);
        sale = saleService.addItem(sale, code, BigDecimal.ONE);
        sale = saleService.goToPayment(sale);
        if (method == PaymentMethod.CARTAO) {
            saleService.payCard(sale, cardType);
        } else if (method == PaymentMethod.PIX) {
            saleService.payPix(sale);
        } else {
            saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal(received));
        }
    }

    private ClosingCount count(String cash, String pix, String credit, String debit) {
        return new ClosingCount(new BigDecimal(cash), new BigDecimal(pix), new BigDecimal(credit),
                new BigDecimal(debit), BigDecimal.ZERO, null);
    }

    @Test
    void closingStoresExpectedAndCountedOfEachPaymentMethod() {
        CashRegister closed = closingService.close(register.getId(), count("105.00", "20.00", "30.00", "40.00"));
        assertEquals(CashRegister.Status.CLOSED, closed.getStatus());
        assertEquals(0, new BigDecimal("105.00").compareTo(closed.getExpectedAmount()));
        assertEquals(0, new BigDecimal("20.00").compareTo(closed.getExpectedPix()));
        assertEquals(0, new BigDecimal("30.00").compareTo(closed.getExpectedCredit()));
        assertEquals(0, new BigDecimal("40.00").compareTo(closed.getExpectedDebit()));
        assertEquals(0, new BigDecimal("40.00").compareTo(closed.getCountedDebit()));
        assertEquals(0, BigDecimal.ZERO.compareTo(closed.totalDifference()));
    }

    @Test
    void differencesOfEachMethodAddUpToTheTotalDifference() {
        CashRegister closed = closingService.close(register.getId(), count("100.00", "25.00", "30.00", "38.00"));
        assertEquals(0, new BigDecimal("-5.00").compareTo(closed.getDifference()));
        assertEquals(0, new BigDecimal("-2.00").compareTo(closed.totalDifference()));
    }

    @Test
    void closingRequiresTheCountOfEveryMethodAndRefusesNegatives() {
        assertThrows(IllegalArgumentException.class,
                () -> closingService.close(register.getId(), new ClosingCount(BigDecimal.TEN, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> closingService.close(register.getId(), count("105.00", "-1", "30.00", "40.00")));
        assertEquals(CashRegister.Status.OPEN, cashRegisterService.findOpenByOperator(operator).orElseThrow().getStatus());
    }

    @Test
    void closingAClosedRegisterIsRefused() {
        closingService.close(register.getId(), count("105.00", "20.00", "30.00", "40.00"));
        assertThrows(CashRegisterClosedException.class,
                () -> closingService.close(register.getId(), count("105.00", "20.00", "30.00", "40.00")));
    }

    @Test
    void closingReceiptShowsExpectedCountedAndDifferencePerMethod() {
        CashRegister closed = closingService.close(register.getId(), count("100.00", "25.00", "30.00", "38.00"));
        String receipt = receiptService.buildCashCloseReceipt(closed, cashRegisterService.movements(closed));
        assertTrue(receipt.contains("CONFERENCIA POR FORMA"));
        assertTrue(receipt.contains("Esperado"));
        assertTrue(receipt.contains("Contado"));
        assertTrue(receipt.contains("FALTA R$ 2,00"));
        for (String line : receipt.split("\n")) {
            assertTrue(line.length() <= 40, "linha larga demais: " + line);
        }
    }

    @Test
    void movementsReceiptListsEveryMovementAndTheSummary() {
        String receipt = receiptService.buildCashMovementsReceipt(register, cashSummaryService.overview(register));
        assertTrue(receipt.contains("MOVIMENTACAO DO CAIXA"));
        assertTrue(receipt.contains("SANGRIA"));
        assertTrue(receipt.contains("Saldo em dinheiro"));
        assertEquals(5, receipt.lines().filter(line -> line.contains("Venda #") || line.contains("SANGRIA")).count());
    }

    @Test
    void reopeningTheLastClosedRegisterKeepsEveryMovementAndSale(@TempDir Path backups) throws IOException {
        closingService.close(register.getId(), count("100.00", "25.00", "30.00", "38.00"));
        int movementsBefore = cashRegisterService.movements(register).size();
        int salesBefore = cashRegisterService.sales(register).size();

        CashRegister reopened = reopeningService.reopenLastClosed(operator.getLogin(), "teste", backups).orElseThrow();

        assertEquals(register.getId(), reopened.getId());
        assertEquals(CashRegister.Status.OPEN, reopened.getStatus());
        assertNull(reopened.getClosedAt());
        assertNull(reopened.getExpectedAmount());
        assertNull(reopened.getCountedPix());
        assertEquals(movementsBefore, cashRegisterService.movements(reopened).size());
        assertEquals(salesBefore, cashRegisterService.sales(reopened).size());
        assertTrue(cashRegisterService.findOpenByOperator(operator).isPresent());
        try (var files = Files.list(backups)) {
            Path backup = files.findFirst().orElseThrow();
            String content = Files.readString(backup);
            assertTrue(content.contains("FECHAMENTO DE CAIXA"));
            assertTrue(content.contains("MOVIMENTACAO DO CAIXA"));
        }
        Integer audited = jdbc.queryForObject(
                "select count(*) from cash_register_reopening where cash_register_id = ?", Integer.class,
                register.getId());
        assertEquals(1, audited);
    }

    @Test
    void reopeningDoesNothingWhenTheOperatorAlreadyHasAnOpenRegister(@TempDir Path backups) {
        assertFalse(reopeningService.reopenLastClosed(operator.getLogin(), "teste", backups).isPresent());
    }
}
