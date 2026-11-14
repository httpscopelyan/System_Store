package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ElectronicPaymentReceivedException;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.SplitPaymentInProgressException;
import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditLog;
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
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ReceiptPayment;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
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
class SplitPaymentGuardsTests {

    private static final BigDecimal INITIAL_STOCK = new BigDecimal("100.000");

    @Autowired private SplitPaymentService splitPaymentService;
    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentRepository paymentRepository;

    private User operator;
    private CashRegister register;
    private Product product;

    @BeforeEach
    void prepare() {
        User user = new User();
        user.setName("Guarda");
        user.setLogin("guarda_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);
        operator = userRepository.save(user);
        register = cashRegisterService.open(operator, new BigDecimal("100.00"));
    }

    private Sale saleInPayment(String unitPrice) {
        product = new Product();
        product.setCode(UUID.randomUUID().toString().substring(0, 13));
        product.setDescription("Produto protegido");
        product.setPrice(new BigDecimal(unitPrice));
        product.setQuantity(INITIAL_STOCK);
        product.setUnit(Product.Unidade.UN);
        product.setActive(true);
        productRepository.save(product);
        Sale sale = saleService.openSale(operator, register);
        sale = saleService.addItem(sale, product.getCode(), BigDecimal.ONE);
        return saleService.goToPayment(sale);
    }

    private List<Payment> partsOf(Sale sale) {
        return paymentRepository.findBySale_IdOrderByCreatedAtAscIdAsc(sale.getId());
    }

    @Test
    void reopeningDiscardsCashOnlyPartsAndRecordsTheRemovalInTheAuditLog() {
        Sale sale = saleInPayment("90.00");
        splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("20.00")));
        splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("30.00")));

        Sale reopened = saleService.reopenForEditing(sale);

        assertEquals(Sale.Status.ABERTA, reopened.getStatus());
        assertTrue(partsOf(sale).isEmpty());
        List<AuditLog> removals = auditLogService.byActor(operator.getId(), 0, 10).getContent().stream()
                .filter(entry -> entry.getAction() == AuditAction.PAYMENT_PART_REMOVED).toList();
        assertEquals(2, removals.size());
        assertEquals(AuditAction.PAYMENT_PART_REMOVED, removals.get(0).getAction());
        assertEquals(sale.getId(), removals.get(0).getEntityId());
        assertNull(removals.get(0).getNewValue());
    }

    @Test
    void reopeningIsRefusedWhenACardOrPixPartWasAlreadyReceived() {
        Sale saleWithCard = saleInPayment("90.00");
        splitPaymentService.registerPart(saleWithCard, PaymentInput.card(new BigDecimal("40.00"), CardType.DEBITO));
        Sale saleWithPix = saleInPayment("90.00");
        splitPaymentService.registerPart(saleWithPix, PaymentInput.cash(new BigDecimal("10.00")));
        splitPaymentService.registerPart(saleWithPix, PaymentInput.pix(new BigDecimal("40.00")));

        ElectronicPaymentReceivedException refusal = assertThrows(ElectronicPaymentReceivedException.class,
                () -> saleService.reopenForEditing(saleWithCard));
        assertThrows(ElectronicPaymentReceivedException.class, () -> saleService.reopenForEditing(saleWithPix));

        assertTrue(refusal.getMessage().contains("cartão ou PIX"));
        assertEquals(Sale.Status.EM_PAGAMENTO, saleWithCard.getStatus());
        assertEquals(1, partsOf(saleWithCard).size());
        assertEquals(2, partsOf(saleWithPix).size());
    }

    @Test
    void cancellingIsRefusedWhenACardOrPixPartWasAlreadyReceived() {
        Sale sale = saleInPayment("90.00");
        splitPaymentService.registerPart(sale, PaymentInput.pix(new BigDecimal("40.00")));

        assertThrows(ElectronicPaymentReceivedException.class, () -> saleService.cancelSale(sale));

        assertEquals(Sale.Status.EM_PAGAMENTO, sale.getStatus());
        assertNull(sale.getCanceledAt());
        assertEquals(1, partsOf(sale).size());
    }

    @Test
    void cancellingDiscardsCashOnlyPartsSoTheDrawerBalanceIsNotInflated() {
        Sale sale = saleInPayment("90.00");
        splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("20.00")));

        Sale canceled = saleService.cancelSale(sale);

        assertEquals(Sale.Status.CANCELADA, canceled.getStatus());
        assertTrue(partsOf(sale).isEmpty());
        assertEquals(0, new BigDecimal("100.00").compareTo(cashRegisterService.currentBalance(register)));
    }

    @Test
    void saleWithoutPartsStillReopensAndCancelsAsBefore() {
        Sale reopened = saleService.reopenForEditing(saleInPayment("90.00"));
        assertEquals(Sale.Status.ABERTA, reopened.getStatus());
        assertEquals(Sale.Status.CANCELADA, saleService.cancelSale(saleInPayment("15.00")).getStatus());
    }

    @Test
    void singlePaymentFlowsRefuseASaleThatAlreadyHasParts() {
        Sale sale = saleInPayment("90.00");
        splitPaymentService.registerPart(sale, PaymentInput.card(new BigDecimal("40.00"), CardType.DEBITO));

        assertThrows(SplitPaymentInProgressException.class,
                () -> saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal("90.00")));
        assertThrows(SplitPaymentInProgressException.class, () -> saleService.payCard(sale, CardType.CREDITO));
        assertThrows(SplitPaymentInProgressException.class, () -> saleService.payPix(sale));
        assertThrows(SplitPaymentInProgressException.class, () -> saleService.finalizeOnCredit(sale));
        assertThrows(SplitPaymentInProgressException.class, () -> saleService.payMixed(sale,
                List.of(PaymentInput.cash(new BigDecimal("90.00")))));

        assertEquals(Sale.Status.EM_PAGAMENTO, sale.getStatus());
        assertEquals(1, partsOf(sale).size());
        assertEquals(0, INITIAL_STOCK.compareTo(productRepository.findById(product.getId()).orElseThrow().getQuantity()));
    }

    @Test
    void discountAndSurchargeAreRefusedAfterAPartBecauseTheTotalIsLocked() {
        Sale sale = saleInPayment("90.00");
        splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("40.00")));

        assertThrows(SplitPaymentInProgressException.class,
                () -> saleService.applySaleDiscount(sale, new BigDecimal("60.00")));
        assertThrows(SplitPaymentInProgressException.class,
                () -> saleService.applySurcharge(sale, new BigDecimal("5.00")));
        assertEquals(0, new BigDecimal("90.00").compareTo(sale.getTotal()));
    }

    @Test
    void aSecondFinalizationOfAnAlreadyPaidSaleIsImpossible() {
        Sale sale = saleInPayment("90.00");
        Sale finalized = splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("90.00"))).sale();

        assertThrows(IllegalSaleStateException.class, () -> saleService.payPix(finalized));
        assertThrows(IllegalSaleStateException.class, () -> saleService.reopenForEditing(finalized));
        assertThrows(IllegalSaleStateException.class, () -> saleService.cancelSale(finalized));
        assertEquals(0, new BigDecimal("99").compareTo(
                productRepository.findById(product.getId()).orElseThrow().getQuantity()));
    }

    @Test
    void mixedPaymentNowStoresTheCardTypeOfEachCardPart() {
        Sale sale = saleInPayment("100.00");

        saleService.payMixed(sale, List.of(
                PaymentInput.card(new BigDecimal("60.00"), CardType.DEBITO),
                PaymentInput.card(new BigDecimal("30.00"), CardType.CREDITO),
                PaymentInput.cash(new BigDecimal("10.00"))));

        List<Payment> parts = partsOf(sale);
        assertEquals(CardType.DEBITO, parts.get(0).getCardType());
        assertEquals(CardType.CREDITO, parts.get(1).getCardType());
        assertNull(parts.get(2).getCardType());
    }

    @Test
    void receiptListsEveryPartAndTheChangeOfASplitSale() {
        Sale sale = saleInPayment("90.00");
        splitPaymentService.registerPart(sale, PaymentInput.card(new BigDecimal("40.00"), CardType.DEBITO));
        Sale finalized = splitPaymentService.registerPart(sale, PaymentInput.cash(new BigDecimal("60.00"))).sale();
        List<ReceiptPayment> receiptPayments = splitPaymentService.receiptPayments(finalized);
        ReceiptService receiptService = new ReceiptService(new AppSettingService(null) {
            @Override
            public String getString(String key, String defaultValue) {
                return defaultValue;
            }
        }, new PrinterPort() {
            @Override
            public void print(String text) {
            }

            @Override
            public void openDrawer() {
            }
        });

        String receipt = receiptService.buildSplitSaleReceipt(finalized, receiptPayments);
        String copy = receiptService.buildSplitSaleReceiptCopy(finalized, receiptPayments);

        assertEquals(2, receiptPayments.size());
        assertTrue(receipt.contains("PAGAMENTO DIVIDIDO"));
        assertTrue(receipt.lines().anyMatch(line -> line.startsWith("Cartao de Debito") && line.endsWith("R$ 40,00")));
        assertTrue(receipt.lines().anyMatch(line -> line.startsWith("Dinheiro") && line.endsWith("R$ 60,00")));
        assertTrue(receipt.lines().anyMatch(line -> line.startsWith("Troco") && line.endsWith("R$ 10,00")));
        assertFalse(receipt.contains("2a VIA"));
        assertTrue(copy.contains("2a VIA"));
        for (String line : receipt.split("\n")) {
            assertTrue(line.length() <= 40, "linha larga demais: " + line);
        }
    }
}
