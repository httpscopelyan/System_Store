package br.com.scopel.pfv.beta;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Item A (QA / Time de Validação): resumo de fechamento de caixa com cartão
 * separado por {@link CardType} (débito x crédito) e saldo de gaveta só com
 * dinheiro.
 *
 * <p>A <b>decisão de UI</b> (débito → coluna Débito, crédito/legado → coluna
 * Crédito, somatórios do resumo) vive em {@code CaixaController.buildMovimentacaoAndResumo}
 * (controller JavaFX, @FXML) e NÃO é testável unitariamente sem o toolkit JavaFX.
 * Aqui cobre-se o que o <b>service</b> garante e que a UI consome:
 * <ul>
 *   <li>{@code payCard} grava o {@link CardType} certo em cada {@link Payment};</li>
 *   <li>{@code payments(caixa)} devolve os pagamentos do caixa com o cardType
 *       para a UI classificar débito x crédito;</li>
 *   <li>cartão/legado sem cardType (null) persiste e é recuperável (fallback
 *       seguro da UI = crédito);</li>
 *   <li>a gaveta ({@code currentBalance}) é SÓ dinheiro + suprimento − sangria;
 *       cartão e PIX ficam de fora.</li>
 * </ul>
 * A regra de classificação espelhada em {@link #cardColumn(Payment)} reproduz o
 * ramo do controller (linhas 197-201) para dar valor de regressão sobre os
 * dados reais — não substitui o teste do controller.
 */
@SpringBootTest
@Transactional
class CashSummaryResumoTests {

    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentRepository paymentRepository;

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

    private Product newProduct(BigDecimal price, BigDecimal stock, Product.Unidade unit) {
        Product product = new Product();
        product.setCode(UUID.randomUUID().toString().substring(0, 13));
        product.setDescription("P " + UUID.randomUUID());
        product.setPrice(price);
        product.setQuantity(stock);
        product.setUnit(unit);
        product.setActive(true);
        return productRepository.save(product);
    }

    private Sale finalizedCard(User op, CashRegister caixa, BigDecimal unitPrice, BigDecimal qty, CardType type) {
        Product product = newProduct(unitPrice, new BigDecimal("100.000"), Product.Unidade.UN);
        Sale sale = saleService.openSale(op, caixa);
        sale = saleService.addItem(sale, product.getCode(), qty);
        sale = saleService.goToPayment(sale);
        return saleService.payCard(sale, type);
    }

    private Payment paymentFor(Sale sale) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getSale().getId().equals(sale.getId()))
                .findFirst().orElseThrow();
    }

    /** Espelha o ramo de CaixaController: débito → "DEBITO", resto (incl. null) → "CREDITO". */
    private static String cardColumn(Payment p) {
        return p.getCardType() == CardType.DEBITO ? "DEBITO" : "CREDITO";
    }

    private static BigDecimal net(Payment p) {
        BigDecimal received = p.getAmountReceived() == null ? BigDecimal.ZERO : p.getAmountReceived();
        BigDecimal change = p.getChange() == null ? BigDecimal.ZERO : p.getChange();
        return received.subtract(change);
    }

    // ---- débito x crédito separados no dado que a UI consome ----

    @Test
    void debitSaleIsClassifiedAsDebitNotCredit() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);

        Sale sale = finalizedCard(op, caixa, new BigDecimal("25.00"), new BigDecimal("2"), CardType.DEBITO); // 50.00

        Payment payment = paymentFor(sale);
        assertEquals(PaymentMethod.CARTAO, payment.getMethod());
        assertEquals(CardType.DEBITO, payment.getCardType());
        // Regressão do bug antigo (todo cartão caía em crédito): débito NÃO é crédito.
        assertEquals("DEBITO", cardColumn(payment));
        assertEquals(0, new BigDecimal("50.00").compareTo(net(payment)));
    }

    @Test
    void creditSaleIsClassifiedAsCredit() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);

        Sale sale = finalizedCard(op, caixa, new BigDecimal("40.00"), new BigDecimal("1"), CardType.CREDITO);

        Payment payment = paymentFor(sale);
        assertEquals(CardType.CREDITO, payment.getCardType());
        assertEquals("CREDITO", cardColumn(payment));
    }

    @Test
    void legacyCartaoWithoutCardTypePersistsAndFallsBackToCredit() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);

        // Simula dado legado: pagamento CARTAO sem cardType (null) ligado a uma venda real.
        Sale sale = saleService.openSale(op, caixa);
        Product product = newProduct(new BigDecimal("30.00"), new BigDecimal("100.000"), Product.Unidade.UN);
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("1"));
        sale = saleService.goToPayment(sale);
        sale = saleService.payPix(sale); // só para levar a venda a FINALIZADA de forma válida

        Payment legacy = new Payment();
        legacy.setSale(sale);
        legacy.setMethod(PaymentMethod.CARTAO);
        legacy.setCardType(null); // legado
        legacy.setAmountReceived(new BigDecimal("30.00"));
        legacy.setChange(new BigDecimal("0.00"));
        legacy.setCreatedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(legacy);

        // Persiste com cardType null e é recuperável pelo caixa.
        List<Payment> fromRegister = cashRegisterService.payments(caixa);
        Payment reread = fromRegister.stream()
                .filter(p -> p.getId().equals(saved.getId()))
                .findFirst().orElseThrow();
        assertNull(reread.getCardType(), "cartão legado mantém cardType null");
        // Fallback seguro da UI: cartão sem tipo entra em CRÉDITO (nunca estoura NPE).
        assertEquals("CREDITO", cardColumn(reread));
    }

    // ---- gaveta = só dinheiro + suprimento - sangria (cartão/PIX fora) ----

    @Test
    void cardAndPixSalesDoNotEnterCashDrawer() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op); // fundo 100.00

        finalizedCard(op, caixa, new BigDecimal("40.00"), new BigDecimal("1"), CardType.DEBITO);
        finalizedCard(op, caixa, new BigDecimal("55.00"), new BigDecimal("1"), CardType.CREDITO);

        // PIX
        Sale pix = saleService.openSale(op, caixa);
        Product pp = newProduct(new BigDecimal("10.00"), new BigDecimal("100.000"), Product.Unidade.UN);
        pix = saleService.addItem(pix, pp.getCode(), new BigDecimal("1"));
        pix = saleService.goToPayment(pix);
        saleService.payPix(pix);

        // Nada disso toca a gaveta: segue só o fundo de abertura.
        assertEquals(0, new BigDecimal("100.00").compareTo(cashRegisterService.currentBalance(caixa)));
    }

    @Test
    void cashDrawerIsCashPlusSupplementMinusWithdrawalOnly() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op); // fundo 100.00

        // venda dinheiro 20,00 (sem troco) -> entra na gaveta
        Sale cash = saleService.openSale(op, caixa);
        Product pc = newProduct(new BigDecimal("10.00"), new BigDecimal("100.000"), Product.Unidade.UN);
        cash = saleService.addItem(cash, pc.getCode(), new BigDecimal("2"));
        cash = saleService.goToPayment(cash);
        saleService.pay(cash, PaymentMethod.DINHEIRO, new BigDecimal("20.00"));

        // venda dinheiro 15,00 recebendo 20,00 -> líquido 15,00 na gaveta (troco 5 sai)
        Sale cash2 = saleService.openSale(op, caixa);
        Product pc2 = newProduct(new BigDecimal("15.00"), new BigDecimal("100.000"), Product.Unidade.UN);
        cash2 = saleService.addItem(cash2, pc2.getCode(), new BigDecimal("1"));
        cash2 = saleService.goToPayment(cash2);
        saleService.pay(cash2, PaymentMethod.DINHEIRO, new BigDecimal("20.00"));

        // cartão 40,00 -> NÃO entra na gaveta
        finalizedCard(op, caixa, new BigDecimal("40.00"), new BigDecimal("1"), CardType.DEBITO);

        cashRegisterService.supplement(caixa.getId(), new BigDecimal("50.00"), "reforço");
        cashRegisterService.withdrawal(caixa.getId(), new BigDecimal("30.00"), "retirada");

        // gaveta = 100 + 20 + 15 + 50 - 30 = 155,00  (cartão de fora)
        assertEquals(0, new BigDecimal("155.00").compareTo(cashRegisterService.currentBalance(caixa)));

        // e o fechamento usa exatamente esse esperado
        CashRegister fechado = cashRegisterService.close(caixa.getId(), new BigDecimal("155.00"));
        assertEquals(0, new BigDecimal("155.00").compareTo(fechado.getExpectedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fechado.getDifference()));
    }

    @Test
    void entradasTotalEqualsCashPlusCreditPlusDebitPlusSupplement() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);

        // dinheiro 20,00 líquido
        Sale cash = saleService.openSale(op, caixa);
        Product pc = newProduct(new BigDecimal("20.00"), new BigDecimal("100.000"), Product.Unidade.UN);
        cash = saleService.addItem(cash, pc.getCode(), new BigDecimal("1"));
        cash = saleService.goToPayment(cash);
        saleService.pay(cash, PaymentMethod.DINHEIRO, new BigDecimal("20.00"));

        finalizedCard(op, caixa, new BigDecimal("30.00"), new BigDecimal("1"), CardType.CREDITO); // crédito 30
        finalizedCard(op, caixa, new BigDecimal("45.00"), new BigDecimal("1"), CardType.DEBITO);  // débito 45
        cashRegisterService.supplement(caixa.getId(), new BigDecimal("10.00"), "reforço");

        // Reproduz a fórmula do resumo sobre os dados reais do service.
        BigDecimal dinheiro = BigDecimal.ZERO;
        BigDecimal credito = BigDecimal.ZERO;
        BigDecimal debito = BigDecimal.ZERO;
        for (Payment p : cashRegisterService.payments(caixa)) {
            if (p.getMethod() == PaymentMethod.DINHEIRO) {
                dinheiro = dinheiro.add(net(p));
            } else if (p.getMethod() == PaymentMethod.CARTAO) {
                if ("DEBITO".equals(cardColumn(p))) {
                    debito = debito.add(net(p));
                } else {
                    credito = credito.add(net(p));
                }
            }
        }
        BigDecimal suprimento = new BigDecimal("10.00");
        BigDecimal entradasDinheiro = dinheiro.add(suprimento);
        BigDecimal entradasTotal = entradasDinheiro.add(credito).add(debito);

        assertEquals(0, new BigDecimal("20.00").compareTo(dinheiro));
        assertEquals(0, new BigDecimal("30.00").compareTo(credito));
        assertEquals(0, new BigDecimal("45.00").compareTo(debito));
        // total de entradas = dinheiro(20) + suprimento(10) + crédito(30) + débito(45) = 105,00
        assertEquals(0, new BigDecimal("105.00").compareTo(entradasTotal));
    }
}
