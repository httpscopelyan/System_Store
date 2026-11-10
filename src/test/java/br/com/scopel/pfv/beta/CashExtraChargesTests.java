package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.SplitPaymentInProgressException;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.DirectCharge;
import br.com.scopel.pfv.beta.model.PaymentInput;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.DirectChargeService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
import br.com.scopel.pfv.beta.service.cash.CashClosingService;
import br.com.scopel.pfv.beta.service.cash.CashMovementRow;
import br.com.scopel.pfv.beta.service.cash.CashOverview;
import br.com.scopel.pfv.beta.service.cash.CashReopeningService;
import br.com.scopel.pfv.beta.service.cash.CashSummary;
import br.com.scopel.pfv.beta.service.cash.CashSummaryService;
import br.com.scopel.pfv.beta.service.cash.ClosingCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class CashExtraChargesTests {

    @Autowired private DirectChargeService directChargeService;
    @Autowired private CashSummaryService cashSummaryService;
    @Autowired private CashClosingService closingService;
    @Autowired private CashReopeningService reopeningService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private SplitPaymentService splitPaymentService;
    @Autowired private SaleService saleService;
    @Autowired private ProductService productService;
    @Autowired private ReceiptService receiptService;
    @Autowired private UserRepository userRepository;

    private User operator;
    private CashRegister register;

    @BeforeEach
    void prepare() {
        User user = new User();
        user.setName("Conferente");
        user.setLogin("extra_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);
        operator = userRepository.save(user);
        register = cashRegisterService.open(operator, new BigDecimal("100.00"));
    }

    private Sale saleInPayment(String price) {
        String code = "ext " + UUID.randomUUID().toString().substring(0, 8);
        productService.create(code, "Item " + code, Product.Unidade.UN, new BigDecimal(price), BigDecimal.ONE,
                new BigDecimal("100"), BigDecimal.ZERO, null, true);
        Sale sale = saleService.openSale(operator, register);
        sale = saleService.addItem(sale, code, BigDecimal.ONE);
        return saleService.goToPayment(sale);
    }

    private DirectCharge charge(String amount, PaymentMethod method, CardType cardType, String note) {
        return directChargeService.register(new BigDecimal(amount), method, cardType, operator, register, note);
    }

    private ClosingCount counted(String cash, String extra, String extraNote) {
        return new ClosingCount(new BigDecimal(cash), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                extra == null ? null : new BigDecimal(extra), extraNote);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + " mas veio " + actual);
    }

    @Test
    void chargeNoteIsTrimmedBlankBecomesNullAndIsLimitedTo155Characters() {
        assertEquals("Recarga de celular", charge("10.00", PaymentMethod.PIX, null, "  Recarga de celular  ").getNote());
        assertNull(charge("10.00", PaymentMethod.PIX, null, "   ").getNote());
        assertNull(charge("10.00", PaymentMethod.PIX, null, null).getNote());
        assertNull(directChargeService.register(BigDecimal.TEN, PaymentMethod.PIX, null, operator, register).getNote());
        assertEquals(155, charge("10.00", PaymentMethod.PIX, null, "a".repeat(155)).getNote().length());
        assertThrows(IllegalArgumentException.class, () -> charge("10.00", PaymentMethod.PIX, null, "a".repeat(156)));
    }

    @Test
    void directChargesFormTheExtraTotalAndNeverEnterTheDrawerOrTheSaleTotals() {
        charge("25.00", PaymentMethod.CARTAO, CardType.DEBITO, "Conta de luz");
        charge("10.50", PaymentMethod.PIX, null, null);
        charge("4.50", PaymentMethod.CARTAO, CardType.CREDITO, null);

        CashSummary summary = cashSummaryService.overview(register).summary();

        assertMoney("40.00", summary.extra());
        assertMoney("0.00", summary.pix());
        assertMoney("0.00", summary.credit());
        assertMoney("0.00", summary.debit());
        assertMoney("0.00", summary.cashIn());
        assertMoney("40.00", summary.totalIn());
        assertMoney("100.00", summary.finalBalance());
        assertMoney("100.00", cashRegisterService.currentBalance(register));
    }

    @Test
    void eachDirectChargeIsListedAsAnEntryWithItsFormAndNote() {
        charge("25.00", PaymentMethod.CARTAO, CardType.DEBITO, "Conta de luz");
        charge("10.50", PaymentMethod.PIX, null, null);
        charge("4.50", PaymentMethod.CARTAO, CardType.CREDITO, " ");

        List<CashMovementRow> rows = cashSummaryService.overview(register).rows();

        assertEquals(3, rows.size());
        assertEquals("COBRANÇA (Débito) - Conta de luz", rows.get(0).history());
        assertEquals("COBRANÇA (PIX)", rows.get(1).history());
        assertEquals("COBRANÇA (Crédito)", rows.get(2).history());
        for (CashMovementRow row : rows) {
            assertTrue(row.entry());
            assertTrue(row.isDirectCharge());
            assertFalse(row.isRectifiable());
            assertNull(row.saleId());
            assertMoney("0.00", row.cash().add(row.pix()).add(row.credit()).add(row.debit()));
            assertEquals(0, row.extra().compareTo(row.total()));
        }
    }

    @Test
    void chargesOfAnotherRegisterOrWithoutRegisterStayOutOfTheSummary() {
        directChargeService.register(new BigDecimal("99.00"), PaymentMethod.PIX, null, operator, null, "sem caixa");

        CashOverview overview = cashSummaryService.overview(register);

        assertTrue(overview.rows().isEmpty());
        assertMoney("0.00", overview.summary().extra());
        assertTrue(directChargeService.chargesOf(null).isEmpty());
    }

    @Test
    void splitSaleCountsEachPartInItsOwnFormWithTheChangeTakenOnlyFromCash() {
        Sale sale = saleInPayment("90.00");
        splitPaymentService.registerPart(sale, PaymentInput.card(new BigDecimal("40.00"), CardType.DEBITO));
        splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("100.00")));
        charge("15.00", PaymentMethod.PIX, null, "Recarga");

        CashOverview overview = cashSummaryService.overview(register);
        CashSummary summary = overview.summary();

        assertMoney("40.00", summary.debit());
        assertMoney("0.00", summary.credit());
        assertMoney("0.00", summary.pix());
        assertMoney("50.00", summary.cashIn());
        assertMoney("15.00", summary.extra());
        assertMoney("105.00", summary.totalIn());
        assertMoney("150.00", summary.finalBalance());
        assertMoney("150.00", cashRegisterService.currentBalance(register));
        CashMovementRow saleRow = overview.rows().stream().filter(row -> row.saleId() != null).findFirst().orElseThrow();
        assertMoney("50.00", saleRow.cash());
        assertMoney("40.00", saleRow.debit());
        assertMoney("90.00", saleRow.total());
        assertFalse(saleRow.isDirectCharge());
    }

    @Test
    void partsOfASaleStillInPaymentStayOutOfEveryTotal() {
        Sale sale = saleInPayment("90.00");
        splitPaymentService.registerPart(sale, PaymentInput.card(new BigDecimal("40.00"), CardType.CREDITO));
        splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("10.00")));

        CashOverview overview = cashSummaryService.overview(register);

        assertTrue(overview.rows().isEmpty());
        assertMoney("0.00", overview.summary().credit());
        assertMoney("0.00", overview.summary().cashIn());
        assertMoney("0.00", overview.summary().totalIn());
        assertMoney("100.00", overview.summary().finalBalance());
        assertMoney("100.00", cashRegisterService.currentBalance(register));
    }

    @Test
    void registerCanNotBeClosedWhileASplitSaleIsHalfPaid() {
        Sale sale = saleInPayment("90.00");
        splitPaymentService.registerPart(sale, PaymentInput.card(new BigDecimal("40.00"), CardType.CREDITO));

        assertThrows(SplitPaymentInProgressException.class,
                () -> closingService.close(register.getId(), counted("100.00", "0.00", null)));
        assertThrows(SplitPaymentInProgressException.class,
                () -> cashRegisterService.close(register.getId(), new BigDecimal("100.00")));

        splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("50.00")));
        CashRegister closed = closingService.close(register.getId(), new ClosingCount(new BigDecimal("150.00"),
                BigDecimal.ZERO, new BigDecimal("40.00"), BigDecimal.ZERO, BigDecimal.ZERO, null));
        assertMoney("0.00", closed.totalDifference());
    }

    @Test
    void closingWithoutExtraDifferenceNeedsNoNote() {
        charge("40.00", PaymentMethod.PIX, null, null);

        CashRegister closed = closingService.close(register.getId(), counted("100.00", "40.00", "  "));

        assertMoney("40.00", closed.getExpectedExtra());
        assertMoney("40.00", closed.getCountedExtra());
        assertNull(closed.getExtraNote());
        assertMoney("0.00", closed.extraDifference());
        assertMoney("0.00", closed.totalDifference());
    }

    @Test
    void closingWithoutChargesAcceptsZeroExtraAndKeepsAnOptionalNote() {
        CashRegister closed = closingService.close(register.getId(), counted("100.00", "0", " Sem cobranças "));

        assertMoney("0.00", closed.getExpectedExtra());
        assertEquals("Sem cobranças", closed.getExtraNote());
        assertMoney("0.00", closed.totalDifference());
    }

    @Test
    void closingWithExtraDifferenceRequiresTheNote() {
        charge("40.00", PaymentMethod.PIX, null, null);

        IllegalArgumentException missingNote = assertThrows(IllegalArgumentException.class,
                () -> closingService.close(register.getId(), counted("100.00", "35.00", null)));
        assertThrows(IllegalArgumentException.class,
                () -> closingService.close(register.getId(), counted("100.00", "40.01", "   ")));

        assertTrue(missingNote.getMessage().contains("observação do Extra"));
        assertEquals(CashRegister.Status.OPEN, cashRegisterService.findOpenByOperator(operator).orElseThrow().getStatus());
    }

    @Test
    void extraDifferenceWithNoteEntersTheTotalDifference() {
        charge("40.00", PaymentMethod.PIX, null, null);

        CashRegister closed = closingService.close(register.getId(),
                counted("98.00", "35.00", "Cliente estornou R$ 5 no app"));

        assertEquals("Cliente estornou R$ 5 no app", closed.getExtraNote());
        assertMoney("-5.00", closed.extraDifference());
        assertMoney("-2.00", closed.getDifference());
        assertMoney("-7.00", closed.totalDifference());
    }

    @Test
    void countedExtraIsRequiredNonNegativeAndTheNoteIsLimited() {
        assertThrows(IllegalArgumentException.class,
                () -> closingService.close(register.getId(), counted("100.00", null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> closingService.close(register.getId(), counted("100.00", "-0.01", "negativo")));
        assertThrows(IllegalArgumentException.class,
                () -> closingService.close(register.getId(), counted("100.00", "0.00", "a".repeat(156))));
        assertEquals(CashRegister.Status.OPEN, cashRegisterService.findOpenByOperator(operator).orElseThrow().getStatus());
    }

    @Test
    void registerClosedBeforeTheExtraColumnsExistedStillWorks() {
        CashRegister legacy = closingService.close(register.getId(), counted("95.00", "0.00", null));
        legacy.setExpectedExtra(null);
        legacy.setCountedExtra(null);
        legacy.setExtraNote(null);

        assertMoney("0.00", legacy.extraDifference());
        assertMoney("-5.00", legacy.totalDifference());
        String receipt = receiptService.buildCashCloseReceipt(legacy);
        assertTrue(receipt.contains("CONFERENCIA POR FORMA"));
        assertFalse(receipt.contains("Extra"));

        legacy.setExpectedPix(null);
        legacy.setDifference(null);
        assertMoney("0.00", legacy.totalDifference());
        assertFalse(receiptService.buildCashCloseReceipt(legacy).contains("CONFERENCIA POR FORMA"));
    }

    @Test
    void closingReceiptShowsTheExtraRowAndItsNote() {
        charge("40.00", PaymentMethod.CARTAO, CardType.DEBITO, null);
        CashRegister closed = closingService.close(register.getId(), counted("100.00", "35.00",
                "Maquininha registrou uma cobrança cancelada pelo cliente no aplicativo do banco"));

        String receipt = receiptService.buildCashCloseReceipt(closed, cashRegisterService.movements(closed));

        assertTrue(receipt.lines().anyMatch(line -> line.startsWith("Extra") && line.contains("40,00")
                && line.contains("35,00") && line.contains("-5,00")));
        assertTrue(receipt.contains("Obs. extra: Maquininha registrou"));
        assertTrue(receipt.contains("FALTA R$ 5,00"));
        for (String line : receipt.split("\n")) {
            assertTrue(line.length() <= 40, "linha larga demais: " + line);
        }
    }

    @Test
    void movementsReceiptListsTheChargeAndTheExtraTotal() {
        charge("40.00", PaymentMethod.CARTAO, CardType.DEBITO, "Conta de luz");

        String receipt = receiptService.buildCashMovementsReceipt(register, cashSummaryService.overview(register));

        assertTrue(receipt.lines().anyMatch(line -> line.contains("COBRANÇA (Débito)") && line.endsWith("+R$ 40,00")));
        assertTrue(receipt.lines().anyMatch(line -> line.startsWith("Entrada extra") && line.endsWith("R$ 40,00")));
        for (String line : receipt.split("\n")) {
            assertTrue(line.length() <= 40, "linha larga demais: " + line);
        }
    }

    @Test
    void reopeningAClosedRegisterClearsTheExtraConference(@TempDir Path backups) {
        charge("40.00", PaymentMethod.PIX, null, null);
        closingService.close(register.getId(), counted("100.00", "35.00", "faltou"));

        CashRegister reopened = reopeningService.reopenLastClosed(operator.getLogin(), "teste", backups).orElseThrow();

        assertNull(reopened.getExpectedExtra());
        assertNull(reopened.getCountedExtra());
        assertNull(reopened.getExtraNote());
        assertMoney("40.00", cashSummaryService.overview(reopened).summary().extra());
    }
}
