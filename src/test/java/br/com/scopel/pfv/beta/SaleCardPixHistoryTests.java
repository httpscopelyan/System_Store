package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cobertura dos tenders CARTAO (débito/crédito) e PIX e do histórico de vendas
 * (REVISÕES2). Mesmo padrão @SpringBootTest @Transactional de {@link SaleServiceTests}
 * — precisa do container PostgreSQL de pé para executar; aqui só se garante que
 * compila (Docker indisponível na máquina do QA).
 */
@SpringBootTest
@Transactional
class SaleCardPixHistoryTests {

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
        product.setCode(UUID.randomUUID().toString().substring(0, 13));
        product.setDescription(description);
        product.setPrice(price);
        product.setQuantity(stock);
        product.setUnit(unit);
        product.setActive(true);
        return productRepository.save(product);
    }

    private Payment onlyPaymentForSale(Sale sale) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getSale().getId().equals(sale.getId()))
                .findFirst()
                .orElseThrow();
    }

    private long countPaymentsForSale(Sale sale) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getSale().getId().equals(sale.getId()))
                .count();
    }

    private Sale saleInPayment(User operator, CashRegister caixa, Product product, BigDecimal qty) {
        Sale sale = saleService.openSale(operator, caixa);
        sale = saleService.addItem(sale, product.getCode(), qty);
        return saleService.goToPayment(sale);
    }

    // ---- payCard ----

    @Test
    void payCardDebitFinalizesDecreasesStockAndPersistsCardPayment() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Product product = newProduct("Queijo", new BigDecimal("12.50"), new BigDecimal("30.000"), Product.Unidade.UN);

        Sale sale = saleInPayment(operator, caixa, product, new BigDecimal("2")); // total 25.00
        sale = saleService.payCard(sale, CardType.DEBITO);

        assertEquals(Sale.Status.FINALIZADA, sale.getStatus());
        // RN10: estoque baixado
        assertEquals(0, new BigDecimal("28.000").compareTo(productService.findCode(product.getCode()).getQuantity()));

        assertEquals(1, countPaymentsForSale(sale));
        Payment payment = onlyPaymentForSale(sale);
        assertEquals(PaymentMethod.CARTAO, payment.getMethod());
        assertEquals(CardType.DEBITO, payment.getCardType());
        assertEquals(0, new BigDecimal("25.00").compareTo(payment.getAmountReceived()));
        assertEquals(0, new BigDecimal("0.00").compareTo(payment.getChange()));
        assertEquals(2, payment.getChange().scale(), "troco com escala 2");
        assertNotNull(payment.getCreatedAt());
    }

    @Test
    void payCardCreditFinalizesWithCorrectCardType() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Product product = newProduct("Vinho", new BigDecimal("40.00"), new BigDecimal("10.000"), Product.Unidade.UN);

        Sale sale = saleInPayment(operator, caixa, product, new BigDecimal("1")); // total 40.00
        sale = saleService.payCard(sale, CardType.CREDITO);

        assertEquals(Sale.Status.FINALIZADA, sale.getStatus());
        Payment payment = onlyPaymentForSale(sale);
        assertEquals(PaymentMethod.CARTAO, payment.getMethod());
        assertEquals(CardType.CREDITO, payment.getCardType());
        assertEquals(0, new BigDecimal("40.00").compareTo(payment.getAmountReceived()));
        assertEquals(0, new BigDecimal("0.00").compareTo(payment.getChange()));
    }

    @Test
    void payCardOnOpenSaleThrows() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Product product = newProduct("Suco", new BigDecimal("5.00"), new BigDecimal("20.000"), Product.Unidade.UN);

        Sale sale = saleService.openSale(operator, caixa);
        final Sale open = saleService.addItem(sale, product.getCode(), new BigDecimal("1")); // ABERTA

        assertThrows(IllegalSaleStateException.class, () -> saleService.payCard(open, CardType.DEBITO));
    }

    @Test
    void payCardOnFinalizedSaleThrows() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Product product = newProduct("Chocolate", new BigDecimal("6.00"), new BigDecimal("50.000"), Product.Unidade.UN);

        Sale sale = saleInPayment(operator, caixa, product, new BigDecimal("1"));
        final Sale finalized = saleService.payCard(sale, CardType.DEBITO); // FINALIZADA

        assertThrows(IllegalSaleStateException.class, () -> saleService.payCard(finalized, CardType.CREDITO));
    }

    // ---- payPix ----

    @Test
    void payPixFinalizesWithPixPaymentNullCardTypeAndZeroChange() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Product product = newProduct("Detergente", new BigDecimal("3.49"), new BigDecimal("40.000"), Product.Unidade.UN);

        Sale sale = saleInPayment(operator, caixa, product, new BigDecimal("3")); // total 10.47
        sale = saleService.payPix(sale);

        assertEquals(Sale.Status.FINALIZADA, sale.getStatus());
        assertEquals(0, new BigDecimal("37.000").compareTo(productService.findCode(product.getCode()).getQuantity()));

        Payment payment = onlyPaymentForSale(sale);
        assertEquals(PaymentMethod.PIX, payment.getMethod());
        assertNull(payment.getCardType(), "PIX não tem cardType");
        assertEquals(0, new BigDecimal("10.47").compareTo(payment.getAmountReceived()));
        assertEquals(0, new BigDecimal("0.00").compareTo(payment.getChange()));
    }

    @Test
    void payPixOnOpenSaleThrows() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Product product = newProduct("Sabonete", new BigDecimal("2.00"), new BigDecimal("30.000"), Product.Unidade.UN);

        Sale sale = saleService.openSale(operator, caixa);
        final Sale open = saleService.addItem(sale, product.getCode(), new BigDecimal("1"));

        assertThrows(IllegalSaleStateException.class, () -> saleService.payPix(open));
    }

    @Test
    void payPixOnFinalizedSaleThrows() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Product product = newProduct("Shampoo", new BigDecimal("9.90"), new BigDecimal("25.000"), Product.Unidade.UN);

        Sale sale = saleInPayment(operator, caixa, product, new BigDecimal("1"));
        final Sale finalized = saleService.payPix(sale); // FINALIZADA

        assertThrows(IllegalSaleStateException.class, () -> saleService.payPix(finalized));
    }

    // ---- history ----

    @Test
    void historyReturnsOnlyFinalizedSalesMostRecentFirst() {
        User operator = newOperator();
        CashRegister caixa = newOpenCashRegister(operator);
        Product product = newProduct("Agua", new BigDecimal("2.50"), new BigDecimal("100.000"), Product.Unidade.UN);

        // venda 1 finalizada (cartão)
        Sale s1 = saleInPayment(operator, caixa, product, new BigDecimal("1"));
        s1 = saleService.payCard(s1, CardType.DEBITO);

        // venda 2 finalizada (pix), criada depois -> deve vir antes no histórico
        Sale s2 = saleInPayment(operator, caixa, product, new BigDecimal("2"));
        s2 = saleService.payPix(s2);

        // venda 3 ainda ABERTA -> não aparece no histórico
        Sale s3 = saleService.openSale(operator, caixa);
        s3 = saleService.addItem(s3, product.getCode(), new BigDecimal("1"));

        List<Sale> history = saleService.history();

        // todas as retornadas são FINALIZADA
        assertTrue(history.stream().allMatch(s -> s.getStatus() == Sale.Status.FINALIZADA),
                "history só devolve FINALIZADA");
        final Long openId = s3.getId();
        assertTrue(history.stream().noneMatch(s -> s.getId().equals(openId)),
                "venda aberta não entra no histórico");

        // as duas finalizadas presentes e ordenadas por createdAt desc
        List<Long> ids = history.stream().map(Sale::getId).toList();
        assertTrue(ids.contains(s1.getId()));
        assertTrue(ids.contains(s2.getId()));

        for (int i = 0; i < history.size() - 1; i++) {
            assertFalse(history.get(i).getCreatedAt().isBefore(history.get(i + 1).getCreatedAt()),
                    "ordenado por createdAt desc");
        }
    }
}
