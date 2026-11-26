package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.InvalidQuantityException;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.model.Supplier;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.SaleItemRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.LabelService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.PurchaseService;
import br.com.scopel.pfv.beta.service.QuoteService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.ReportService;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.SupplierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PricePerHundredGramsTests {

    @Autowired private ProductService productService;
    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private UserRepository userRepository;
    @Autowired private ReceiptService receiptService;
    @Autowired private LabelService labelService;
    @Autowired private QuoteService quoteService;
    @Autowired private PurchaseService purchaseService;
    @Autowired private SupplierService supplierService;
    @Autowired private ReportService reportService;
    @Autowired private SaleItemRepository saleItemRepository;

    private Product product(Product.Unidade unit, String price, String cost) {
        return productService.create("g " + UUID.randomUUID().toString().substring(0, 8), "Queijo fatiado", unit,
                new BigDecimal(price), new BigDecimal(cost), new BigDecimal("10"), BigDecimal.ZERO, null, true);
    }

    private User operator() {
        User user = new User();
        user.setLogin("g_" + UUID.randomUUID());
        user.setName("Operador de peso");
        user.setPassword("x");
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);
        return userRepository.save(user);
    }

    private Sale openSale() {
        User user = operator();
        CashRegister register = cashRegisterService.open(user, BigDecimal.ZERO);
        return saleService.openSale(user, register);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + ", veio " + actual);
    }

    @Test
    void theLineTotalChargesThePriceForEveryHundredGrams() {
        Product cheese = product(Product.Unidade.G100, "5.99", "3.00");

        Sale sale = saleService.addItem(openSale(), cheese.getCode(), new BigDecimal("0.250"));

        assertMoney("14.98", sale.getTotal());
        assertMoney("5.99", sale.getItems().get(0).getUnitPrice());
    }

    @Test
    void oneHundredGramsCostsExactlyTheRegisteredPrice() {
        Product cheese = product(Product.Unidade.G100, "2.29", "1.00");

        Sale sale = saleService.addItem(openSale(), cheese.getCode(), new BigDecimal("0.100"));

        assertMoney("2.29", sale.getTotal());
    }

    @Test
    void scanningTheSameWeightProductTwiceAddsTheWeights_RN07() {
        Product cheese = product(Product.Unidade.G100, "5.99", "3.00");
        Sale sale = saleService.addItem(openSale(), cheese.getCode(), new BigDecimal("0.150"));

        sale = saleService.addItem(sale, cheese.getCode(), new BigDecimal("0.100"));

        assertEquals(1, sale.getItems().size());
        assertMoney("0.250", sale.getItems().get(0).getQuantity());
        assertMoney("14.98", sale.getTotal());
    }

    @Test
    void theItemDiscountIsLimitedByTheValueOfTheWeighedItem() {
        Product cheese = product(Product.Unidade.G100, "5.99", "3.00");
        Sale sale = saleService.addItem(openSale(), cheese.getCode(), new BigDecimal("0.250"));

        Sale discounted = saleService.applyItemDiscount(sale, cheese.getCode(), new BigDecimal("4.98"));

        assertMoney("10.00", discounted.getTotal());
    }

    @Test
    void weightKeepsAtMostThreeDecimalsOfAKilo() {
        Product cheese = product(Product.Unidade.G100, "5.99", "3.00");
        Sale sale = openSale();

        assertThrows(InvalidQuantityException.class,
                () -> saleService.addItem(sale, cheese.getCode(), new BigDecimal("0.2505")));
    }

    @Test
    void anItemKeepsItsPricingBasisWhenTheProductUnitChangesLater_RN19() {
        Product cheese = product(Product.Unidade.G100, "5.99", "3.00");
        Sale sale = saleService.addItem(openSale(), cheese.getCode(), new BigDecimal("0.100"));
        productService.update(cheese.getId(), "Queijo fatiado", Product.Unidade.KG, new BigDecimal("59.90"),
                new BigDecimal("30.00"), BigDecimal.ZERO, null, true);

        sale = saleService.addItem(sale, cheese.getCode(), new BigDecimal("0.100"));

        SaleItem item = sale.getItems().get(0);
        assertEquals(Product.Unidade.G100, item.getUnit());
        assertMoney("11.98", item.getTotalPrice());
    }

    @Test
    void theReceiptAndTheLabelShowGramsAndThePricePerHundredGrams() {
        Product cheese = product(Product.Unidade.G100, "5.99", "3.00");
        Sale sale = saleService.addItem(openSale(), cheese.getCode(), new BigDecimal("0.250"));

        String receipt = receiptService.buildSaleReceipt(sale);

        assertTrue(receipt.contains("250g x"), receipt);
        assertTrue(receipt.contains("/100g"), receipt);
        assertTrue(receipt.contains("14,98"), receipt);
        assertTrue(labelService.buildLabel(cheese).contains("R$ 5,99/100g"));
    }

    @Test
    void quotesAndPurchasesAlsoPriceEveryHundredGrams() {
        Product cheese = product(Product.Unidade.G100, "5.99", "3.00");
        User user = operator();

        var quote = quoteService.openQuote(null, null, null);
        quote = quoteService.addItem(quote, cheese.getCode(), new BigDecimal("0.500"));
        Supplier supplier = new Supplier();
        supplier.setName("Fornecedor " + UUID.randomUUID());
        supplier = supplierService.save(supplier);
        var purchase = purchaseService.openPurchase(supplier, "NF-G", null);
        purchase = purchaseService.addItem(purchase, cheese.getCode(), new BigDecimal("2"), new BigDecimal("3.00"));

        assertMoney("29.95", quote.getTotal());
        assertMoney("60.00", purchase.getTotal());
    }

    @Test
    void theGrossProfitSubtractsTheCostOfEveryHundredGrams() {
        LocalDateTime start = LocalDateTime.now().minusSeconds(5);
        BigDecimal before = profitSince(start);
        Product cheese = product(Product.Unidade.G100, "5.99", "3.00");
        Sale sale = saleService.addItem(openSale(), cheese.getCode(), new BigDecimal("0.250"));
        sale.setStatus(Sale.Status.FINALIZADA);
        saleItemRepository.flush();

        assertMoney("7.48", profitSince(start).subtract(before));
    }

    private BigDecimal profitSince(LocalDateTime start) {
        return reportService.summary(start, LocalDateTime.now().plusMinutes(1)).grossProfit();
    }
}
