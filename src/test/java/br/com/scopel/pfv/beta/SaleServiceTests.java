package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InsufficientPaymentException;
import br.com.scopel.pfv.beta.exception.PaymentMethodNotAvailableException;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.SaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SaleServiceTests {

    @Autowired
    private SaleService saleService;

    @Autowired
    private CashRegisterService cashRegisterService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private User newOperator() {
        User user = new User();
        user.setName("Operador");
        user.setLogin("op_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);
        return userRepository.save(user);
    }

    private CashRegister newOpenCashRegister(User operator) {
        return cashRegisterService.open(operator, new BigDecimal("100.00"));
    }

    private Product newProduct(String description, BigDecimal price, BigDecimal stock, Product.Unidade unit) {
        Product product = new Product();
        // code column is unique and limited to 15 chars; keep it short
        product.setCode(UUID.randomUUID().toString().substring(0, 13));
        product.setDescription(description);
        product.setPrice(price);
        product.setQuantity(stock);
        product.setUnit(unit);
        product.setActive(true);
        return productRepository.save(product);
    }

    @Test
    void openSaleCreatesOpenSaleWithZeroTotal() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);

        Sale sale = saleService.openSale(operator, caixa);

        assertNotNull(sale.getId());
        assertEquals(Sale.Status.ABERTA, sale.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(sale.getTotal()));
        assertNotNull(sale.getCreatedAt());
        assertEquals(operator.getId(), sale.getOperator().getId());
        assertEquals(caixa.getId(), sale.getCashRegister().getId());
        assertTrue(sale.getItems().isEmpty());
    }

    @Test
    void addItemFreezesUnitPriceAndComputesTotal() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product product = newProduct("Arroz 5kg", new BigDecimal("25.50"), new BigDecimal("100.000"), Product.Unidade.UN);

        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("2"));

        assertEquals(1, sale.getItems().size());
        SaleItem item = sale.getItems().get(0);
        // RN19: unit price is frozen from the product price
        assertEquals(0, new BigDecimal("25.50").compareTo(item.getUnitPrice()));
        // totalPrice = unitPrice * quantity
        assertEquals(0, new BigDecimal("51.00").compareTo(item.getTotalPrice()));
        // sale total matches
        assertEquals(0, new BigDecimal("51.00").compareTo(sale.getTotal()));
    }

    @Test
    void addSameProductTwiceSumsQuantityInSingleItem() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product product = newProduct("Refrigerante", new BigDecimal("7.00"), new BigDecimal("50.000"), Product.Unidade.UN);

        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("1"));
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("2"));

        // RN07: single line, quantities summed
        assertEquals(1, sale.getItems().size());
        SaleItem item = sale.getItems().get(0);
        assertEquals(0, new BigDecimal("3").compareTo(item.getQuantity()));
        assertEquals(0, new BigDecimal("21.00").compareTo(item.getTotalPrice()));
        assertEquals(0, new BigDecimal("21.00").compareTo(sale.getTotal()));
    }

    @Test
    void addItemWithUnknownCodeThrows() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        assertThrows(ProductNotfindException.class, () -> {
            saleService.addItem(sale, "NO_SUCH_CODE", new BigDecimal("1"));
        });
    }

    @Test
    void changingProductPriceAfterAddDoesNotChangeItemPrice() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product product = newProduct("Cafe", new BigDecimal("10.00"), new BigDecimal("30.000"), Product.Unidade.UN);

        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("2"));

        // RN19: change the product price AFTER adding the item
        productService.modificationPrice(product.getCode(), new BigDecimal("99.99"));

        SaleItem item = sale.getItems().get(0);
        assertEquals(0, new BigDecimal("10.00").compareTo(item.getUnitPrice()));
        assertEquals(0, new BigDecimal("20.00").compareTo(item.getTotalPrice()));
        assertEquals(0, new BigDecimal("20.00").compareTo(sale.getTotal()));
    }

    @Test
    void fractionalKgQuantityComputesTotalWithRounding() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        // KG product: 1.234 kg * 9.99 = 12.32766 -> stored with scale 2
        Product product = newProduct("Banana", new BigDecimal("9.99"), new BigDecimal("500.000"), Product.Unidade.KG);

        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("1.234"));

        SaleItem item = sale.getItems().get(0);
        assertEquals(0, new BigDecimal("1.234").compareTo(item.getQuantity()));
        // HALF_UP of 12.32766 at scale 2 = 12.33
        assertEquals(0, new BigDecimal("12.33").compareTo(item.getTotalPrice()));
        assertEquals(0, new BigDecimal("12.33").compareTo(sale.getTotal()));
    }

    @Test
    void payFinalizesDecreasesStockForMixedUnitAndKgItems() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product p1 = newProduct("Feijao", new BigDecimal("8.00"), new BigDecimal("20.000"), Product.Unidade.UN);
        Product p2 = newProduct("Tomate", new BigDecimal("5.00"), new BigDecimal("15.500"), Product.Unidade.KG);

        sale = saleService.addItem(sale, p1.getCode(), new BigDecimal("3"));       // 24.00
        sale = saleService.addItem(sale, p2.getCode(), new BigDecimal("2.250"));   // 11.25 -> total 35.25

        // finalizacao pela via publica: goToPayment -> pay (DINHEIRO, recebido >= total)
        sale = saleService.goToPayment(sale);
        sale = saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal("40.00"));

        // RN10: stock reduced by sold quantity (UN inteiro + KG fracionario)
        Product reloaded1 = productService.findCode(p1.getCode());
        Product reloaded2 = productService.findCode(p2.getCode());
        assertEquals(0, new BigDecimal("17.000").compareTo(reloaded1.getQuantity()));
        assertEquals(0, new BigDecimal("13.250").compareTo(reloaded2.getQuantity()));
        assertEquals(Sale.Status.FINALIZADA, sale.getStatus());
    }

    @Test
    void saleTotalIsSumOfDifferentProducts() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product p1 = newProduct("Leite", new BigDecimal("4.50"), new BigDecimal("40.000"), Product.Unidade.UN);
        Product p2 = newProduct("Pao", new BigDecimal("0.75"), new BigDecimal("200.000"), Product.Unidade.UN);

        sale = saleService.addItem(sale, p1.getCode(), new BigDecimal("2"));   // 9.00
        sale = saleService.addItem(sale, p2.getCode(), new BigDecimal("6"));   // 4.50

        assertEquals(2, sale.getItems().size());
        assertEquals(0, new BigDecimal("13.50").compareTo(sale.getTotal()));
    }

    // ---- Payment flow (goToPayment / pay / calculateChange) + state guard ----

    private long countPaymentsForSale(Sale sale) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getSale().getId().equals(sale.getId()))
                .count();
    }

    private Payment onlyPaymentForSale(Sale sale) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getSale().getId().equals(sale.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void goToPaymentMovesOpenSaleWithItemsToEmPagamento() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product product = newProduct("Acucar", new BigDecimal("4.00"), new BigDecimal("30.000"), Product.Unidade.UN);
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("1"));

        sale = saleService.goToPayment(sale);

        // RN21: ABERTA + itens -> EM_PAGAMENTO
        assertEquals(Sale.Status.EM_PAGAMENTO, sale.getStatus());
    }

    @Test
    void goToPaymentOnEmptySaleThrows() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        // RN21: venda vazia nao pode ir para pagamento
        assertThrows(IllegalSaleStateException.class, () -> saleService.goToPayment(sale));
    }

    @Test
    void payCashWithChangeFinalizesDecreasesStockAndPersistsPayment() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product product = newProduct("Macarrao", new BigDecimal("6.50"), new BigDecimal("40.000"), Product.Unidade.UN);
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("2")); // total 13.00

        sale = saleService.goToPayment(sale);
        sale = saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal("20.00"));

        // FINALIZADA
        assertEquals(Sale.Status.FINALIZADA, sale.getStatus());
        // RN10: estoque baixado
        Product reloaded = productService.findCode(product.getCode());
        assertEquals(0, new BigDecimal("38.000").compareTo(reloaded.getQuantity()));
        // 1 Payment persistido com valores corretos
        assertEquals(1, countPaymentsForSale(sale));
        Payment payment = onlyPaymentForSale(sale);
        assertEquals(PaymentMethod.DINHEIRO, payment.getMethod());
        assertEquals(0, new BigDecimal("20.00").compareTo(payment.getAmountReceived()));
        assertEquals(0, new BigDecimal("7.00").compareTo(payment.getChange()));
        assertNotNull(payment.getCreatedAt());
    }

    @Test
    void payCashExactAmountGivesZeroChangeAndFinalizes() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product product = newProduct("Sal", new BigDecimal("3.25"), new BigDecimal("20.000"), Product.Unidade.UN);
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("4")); // total 13.00

        sale = saleService.goToPayment(sale);
        sale = saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal("13.00"));

        assertEquals(Sale.Status.FINALIZADA, sale.getStatus());
        Payment payment = onlyPaymentForSale(sale);
        // troco 0,00
        assertEquals(0, new BigDecimal("0.00").compareTo(payment.getChange()));
    }

    @Test
    void payCashBelowTotalThrowsAndDoesNotFinalizeNorTouchStock() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product product = newProduct("Oleo", new BigDecimal("7.00"), new BigDecimal("25.000"), Product.Unidade.UN);
        final Sale saleRef = saleService.addItem(sale, product.getCode(), new BigDecimal("2")); // total 14.00
        Sale inPayment = saleService.goToPayment(saleRef);

        // RN09: recebido < total
        assertThrows(InsufficientPaymentException.class,
                () -> saleService.pay(inPayment, PaymentMethod.DINHEIRO, new BigDecimal("13.99")));

        // venda nao finaliza e estoque intacto
        assertEquals(Sale.Status.EM_PAGAMENTO, inPayment.getStatus());
        Product reloaded = productService.findCode(product.getCode());
        assertEquals(0, new BigDecimal("25.000").compareTo(reloaded.getQuantity()));
        assertEquals(0, countPaymentsForSale(inPayment));
    }

    @Test
    void payWithNonCashMethodThrows() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product product = newProduct("Cafe2", new BigDecimal("9.00"), new BigDecimal("10.000"), Product.Unidade.UN);
        final Sale saleRef = saleService.addItem(sale, product.getCode(), new BigDecimal("1"));
        Sale inPayment = saleService.goToPayment(saleRef);

        assertThrows(PaymentMethodNotAvailableException.class,
                () -> saleService.pay(inPayment, PaymentMethod.CARTAO, new BigDecimal("20.00")));
        assertThrows(PaymentMethodNotAvailableException.class,
                () -> saleService.pay(inPayment, PaymentMethod.PIX, new BigDecimal("20.00")));
    }

    @Test
    void payOnStillOpenSaleThrows() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product product = newProduct("Agua", new BigDecimal("2.00"), new BigDecimal("50.000"), Product.Unidade.UN);
        final Sale saleRef = saleService.addItem(sale, product.getCode(), new BigDecimal("1"));

        // sem goToPayment: ainda ABERTA
        assertThrows(IllegalSaleStateException.class,
                () -> saleService.pay(saleRef, PaymentMethod.DINHEIRO, new BigDecimal("10.00")));
    }

    @Test
    void calculateChangeRoundsHalfUpAtScaleTwo() {
        BigDecimal change = saleService.calculateChange(new BigDecimal("12.33"), new BigDecimal("20.00"));
        assertEquals(0, new BigDecimal("7.67").compareTo(change));
        assertEquals(2, change.scale());
    }

    @Test
    void addItemOnSaleInPaymentThrows() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Sale sale = saleService.openSale(operator, caixa);

        Product product = newProduct("Biscoito", new BigDecimal("3.00"), new BigDecimal("30.000"), Product.Unidade.UN);
        final Sale saleRef = saleService.addItem(sale, product.getCode(), new BigDecimal("1"));
        Sale inPayment = saleService.goToPayment(saleRef);

        // RN21: addItem recusado fora de ABERTA
        assertThrows(IllegalSaleStateException.class,
                () -> saleService.addItem(inPayment, product.getCode(), new BigDecimal("1")));
    }
}
