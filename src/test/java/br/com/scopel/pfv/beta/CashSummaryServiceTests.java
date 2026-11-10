package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.cash.CashMovementRow;
import br.com.scopel.pfv.beta.service.cash.CashOverview;
import br.com.scopel.pfv.beta.service.cash.CashSummary;
import br.com.scopel.pfv.beta.service.cash.CashSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class CashSummaryServiceTests {

    @Autowired private CashSummaryService cashSummaryService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private SaleService saleService;
    @Autowired private ProductService productService;
    @Autowired private UserRepository userRepository;

    private User operator;
    private User manager;
    private CashRegister register;

    private User user(User.Role role) {
        User user = new User();
        user.setName(role.name());
        user.setLogin(role.name().toLowerCase() + "_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    @BeforeEach
    void prepare() {
        operator = user(User.Role.OPERADOR);
        manager = user(User.Role.GERENTE);
        register = cashRegisterService.open(operator, new BigDecimal("100.00"));
    }

    private Sale finalizedSale(String price, PaymentMethod method, CardType cardType, String received) {
        String code = "sum " + UUID.randomUUID().toString().substring(0, 8);
        productService.create(code, "Item " + code, Product.Unidade.UN, new BigDecimal(price), BigDecimal.ONE,
                new BigDecimal("100"), BigDecimal.ZERO, null, true);
        Sale sale = saleService.openSale(operator, register);
        sale = saleService.addItem(sale, code, BigDecimal.ONE);
        sale = saleService.goToPayment(sale);
        if (method == PaymentMethod.CARTAO) {
            return saleService.payCard(sale, cardType);
        }
        if (method == PaymentMethod.PIX) {
            return saleService.payPix(sale);
        }
        return saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal(received));
    }

    @Test
    void emptyRegisterSummaryIsTheOpeningAmountOnly_RN15() {
        CashOverview overview = cashSummaryService.overview(register);
        assertTrue(overview.rows().isEmpty());
        assertEquals(0, new BigDecimal("100.00").compareTo(overview.summary().opening()));
        assertEquals(0, new BigDecimal("100.00").compareTo(overview.summary().finalBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(overview.summary().totalIn()));
    }

    @Test
    void salesAreSplitByPaymentFormAndOnlyCashEntersTheDrawer_RN15() {
        finalizedSale("20.00", PaymentMethod.DINHEIRO, null, "20.00");
        finalizedSale("15.00", PaymentMethod.DINHEIRO, null, "20.00");
        finalizedSale("40.00", PaymentMethod.CARTAO, CardType.DEBITO, null);
        finalizedSale("55.00", PaymentMethod.CARTAO, CardType.CREDITO, null);

        CashOverview overview = cashSummaryService.overview(register);
        assertEquals(0, new BigDecimal("35.00").compareTo(overview.summary().cashIn()));
        assertEquals(0, new BigDecimal("40.00").compareTo(overview.summary().debit()));
        assertEquals(0, new BigDecimal("55.00").compareTo(overview.summary().credit()));
        assertEquals(0, new BigDecimal("130.00").compareTo(overview.summary().totalIn()));
        assertEquals(0, new BigDecimal("135.00").compareTo(overview.summary().finalBalance()));
        assertEquals(4, overview.rows().size());
    }

    @Test
    void pixSalesAppearInTheirOwnTotalAndNotInTheDrawer() {
        finalizedSale("20.00", PaymentMethod.DINHEIRO, null, "20.00");
        finalizedSale("30.00", PaymentMethod.PIX, null, null);

        CashOverview overview = cashSummaryService.overview(register);
        assertEquals(0, new BigDecimal("30.00").compareTo(overview.summary().pix()));
        assertEquals(0, new BigDecimal("20.00").compareTo(overview.summary().cashIn()));
        assertEquals(0, new BigDecimal("50.00").compareTo(overview.summary().totalIn()));
        assertEquals(0, new BigDecimal("120.00").compareTo(overview.summary().finalBalance()));
    }

    @Test
    void fullDayWithEveryFormOfPaymentKeepsEachColumnAndTheDrawerCorrect() {
        finalizedSale("20.00", PaymentMethod.DINHEIRO, null, "50.00");
        finalizedSale("30.00", PaymentMethod.PIX, null, null);
        finalizedSale("12.50", PaymentMethod.PIX, null, null);
        finalizedSale("40.00", PaymentMethod.CARTAO, CardType.DEBITO, null);
        finalizedSale("55.00", PaymentMethod.CARTAO, CardType.CREDITO, null);
        cashRegisterService.supplement(register.getId(), new BigDecimal("10"), "troco", register.getOperator());
        cashRegisterService.withdrawal(register.getId(), new BigDecimal("5"), "sangria", register.getOperator());

        CashOverview overview = cashSummaryService.overview(register);
        CashSummary summary = overview.summary();

        assertEquals(0, new BigDecimal("30.00").compareTo(summary.cashIn()));
        assertEquals(0, new BigDecimal("42.50").compareTo(summary.pix()));
        assertEquals(0, new BigDecimal("40.00").compareTo(summary.debit()));
        assertEquals(0, new BigDecimal("55.00").compareTo(summary.credit()));
        assertEquals(0, new BigDecimal("167.50").compareTo(summary.totalIn()));
        assertEquals(0, new BigDecimal("125.00").compareTo(summary.finalBalance()));
        BigDecimal pixOnRows = overview.rows().stream().map(CashMovementRow::pix)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("42.50").compareTo(pixOnRows));
    }

    @Test
    void summaryFinalBalanceAlwaysEqualsTheDrawerBalance_RN15() {
        finalizedSale("20.00", PaymentMethod.DINHEIRO, null, "30.00");
        cashRegisterService.supplement(register.getId(), new BigDecimal("50"), "reforço", register.getOperator());
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("30"), "retirada", register.getOperator());
        assertEquals(0, cashRegisterService.currentBalance(register)
                .compareTo(cashSummaryService.overview(register).summary().finalBalance()));
        cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("10"), "erro de digitação", manager);
        assertEquals(0, cashRegisterService.currentBalance(register)
                .compareTo(cashSummaryService.overview(register).summary().finalBalance()));
    }

    @Test
    void withdrawalRectificationReducesTheWithdrawalTotal_RN29() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("30"), "retirada", register.getOperator());
        cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("10"), "erro de digitação", manager);
        CashOverview overview = cashSummaryService.overview(register);
        assertEquals(0, new BigDecimal("10.00").compareTo(overview.summary().cashOut()));
        assertEquals(0, new BigDecimal("90.00").compareTo(overview.summary().finalBalance()));
    }

    @Test
    void supplementRectificationAdjustsTheCashInTotal_RN29() {
        CashMovement supplement = cashRegisterService.supplement(register.getId(), new BigDecimal("50"), "reforço", register.getOperator());
        cashRegisterService.rectify(supplement.getId(), new BigDecimal("20"), "foi só 20", manager);
        assertEquals(0, new BigDecimal("20.00").compareTo(cashSummaryService.overview(register).summary().cashIn()));
    }

    @Test
    void movementRowsCarryTheReasonAndTheRectificationTrail_RN28() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("30"), "Pagamento fornecedor", register.getOperator());
        cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("10"), "erro de digitação", manager);
        List<CashMovementRow> rows = cashSummaryService.overview(register).rows();
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).history().contains("Pagamento fornecedor"));
        assertTrue(rows.get(1).history().startsWith("RETIFICACAO - erro de digitação"));
        assertTrue(rows.get(1).history().contains("corrige SANGRIA #" + withdrawal.getId()));
        assertTrue(rows.get(0).isRectifiable());
        assertFalse(rows.get(1).isRectifiable());
    }

    @Test
    void closeReceiptListsEveryMovementWithItsReason_RN28() {
        PrinterPort noopPrinter = new PrinterPort() {
            @Override public void print(String content) { }
            @Override public void openDrawer() { }
        };
        ReceiptService receiptService = new ReceiptService(new AppSettingService(null) {
            @Override public String getString(String key, String defaultValue) {
                return defaultValue;
            }
        }, noopPrinter);
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("30"), "Pagamento fornecedor", register.getOperator());
        cashRegisterService.supplement(register.getId(), new BigDecimal("10"), "Troco inicial", register.getOperator());
        register.setClosedAt(LocalDateTime.now());
        String receipt = receiptService.buildCashCloseReceipt(register, cashRegisterService.movements(register));
        assertTrue(receipt.contains("MOVIMENTOS DO CAIXA"));
        assertTrue(receipt.contains("SANGRIA"));
        assertTrue(receipt.contains("Pagamento fornecedor"));
        assertTrue(receipt.contains("Troco inicial"));
        assertTrue(receipt.contains("R$ 30,00"));
        assertEquals(withdrawal.getType(), CashMovement.Type.SANGRIA);
    }

    @Test
    void closeReceiptWithoutMovementsHasNoMovementSection_RN28() {
        PrinterPort noopPrinter = new PrinterPort() {
            @Override public void print(String content) { }
            @Override public void openDrawer() { }
        };
        ReceiptService receiptService = new ReceiptService(new AppSettingService(null) {
            @Override public String getString(String key, String defaultValue) {
                return defaultValue;
            }
        }, noopPrinter);
        assertFalse(receiptService.buildCashCloseReceipt(register).contains("MOVIMENTOS DO CAIXA"));
    }
}
