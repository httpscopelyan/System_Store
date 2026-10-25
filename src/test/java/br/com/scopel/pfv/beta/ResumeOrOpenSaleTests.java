package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.SaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bordas de {@link SaleService#resumeOrOpenSale} (2ª leva "venda pendente").
 * Reusa a venda de sessão SOMENTE se ABERTA/EM_PAGAMENTO, mesmo operador e
 * mesmo caixa (comparação por id, defensiva a nulos); caso contrário abre nova.
 * A decisão vive no service justamente para ser testável.
 */
@SpringBootTest
@Transactional
class ResumeOrOpenSaleTests {

    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SaleRepository saleRepository;

    private User newOperator() {
        User u = new User();
        u.setName("Operador");
        u.setLogin("op_" + UUID.randomUUID());
        u.setPassword("x");
        u.setRole(User.Role.OPERADOR);
        u.setActive(true);
        return userRepository.save(u);
    }

    private User newManager() {
        User u = new User();
        u.setName("Gerente");
        u.setLogin("ger_" + UUID.randomUUID());
        u.setPassword("x");
        u.setRole(User.Role.GERENTE);
        u.setActive(true);
        return userRepository.save(u);
    }

    private CashRegister newOpenCashRegister(User operator) {
        return cashRegisterService.open(operator, new BigDecimal("100.00"));
    }

    private Product newProduct() {
        Product p = new Product();
        p.setCode(UUID.randomUUID().toString().substring(0, 13));
        p.setDescription("Produto");
        p.setPrice(new BigDecimal("5.00"));
        p.setQuantity(new BigDecimal("100.000"));
        p.setUnit(Product.Unidade.UN);
        p.setActive(true);
        return productRepository.save(p);
    }

    /** Venda de sessão "montada à mão" (não persistida) para as bordas de id/nulo. */
    private Sale sessionSale(Sale.Status status, User operator, CashRegister cashRegister) {
        Sale s = new Sale();
        s.setStatus(status);
        s.setOperator(operator);
        s.setCashRegister(cashRegister);
        return s;
    }

    private User userWithId(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private CashRegister cashRegisterWithId(Long id) {
        CashRegister c = new CashRegister();
        c.setId(id);
        return c;
    }

    private long countOpenSalesFor(CashRegister caixa) {
        return saleRepository.findByCashRegister_IdOrderByCreatedAtAsc(caixa.getId()).stream()
                .filter(s -> s.getStatus() == Sale.Status.ABERTA)
                .count();
    }

    // 1. sessionSale == null -> venda NOVA, ABERTA
    @Test
    void nullSessionSaleOpensNewAbertaSale() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);

        Sale result = saleService.resumeOrOpenSale(op, caixa, null);

        assertNotNull(result.getId());
        assertEquals(Sale.Status.ABERTA, result.getStatus());
        assertEquals(op.getId(), result.getOperator().getId());
        assertEquals(caixa.getId(), result.getCashRegister().getId());
        assertTrue(result.getItems().isEmpty());
    }

    // 2. sessionSale ABERTA, mesmo op + mesmo caixa, SEM itens -> MESMA venda
    @Test
    void openSameOwnerResumesSameInstanceEvenWithoutItems() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Sale aberta = saleService.openSale(op, caixa); // ABERTA, sem itens

        Sale result = saleService.resumeOrOpenSale(op, caixa, aberta);

        assertSame(aberta, result);
        assertEquals(aberta.getId(), result.getId());
        assertEquals(Sale.Status.ABERTA, result.getStatus());
        // não criou outra: só a própria venda ABERTA existe no caixa
        assertEquals(1, countOpenSalesFor(caixa));
    }

    // 3. sessionSale EM_PAGAMENTO, mesmo op + mesmo caixa -> MESMA venda
    @Test
    void emPagamentoSameOwnerResumesSameInstance() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Product p = newProduct();
        Sale sale = saleService.openSale(op, caixa);
        sale = saleService.addItem(sale, p.getCode(), new BigDecimal("1"));
        sale = saleService.goToPayment(sale); // EM_PAGAMENTO

        Sale result = saleService.resumeOrOpenSale(op, caixa, sale);

        assertSame(sale, result);
        assertEquals(Sale.Status.EM_PAGAMENTO, result.getStatus());
    }

    // 4. sessionSale FINALIZADA -> abre nova
    @Test
    void finalizadaSessionOpensNew() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Product p = newProduct();
        Sale sale = saleService.openSale(op, caixa);
        sale = saleService.addItem(sale, p.getCode(), new BigDecimal("1"));
        sale = saleService.goToPayment(sale);
        sale = saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal("10.00")); // FINALIZADA

        Sale result = saleService.resumeOrOpenSale(op, caixa, sale);

        assertNotSame(sale, result);
        assertNotEquals(sale.getId(), result.getId());
        assertEquals(Sale.Status.ABERTA, result.getStatus());
    }

    // 5. sessionSale CANCELADA -> abre nova
    @Test
    void canceladaSessionOpensNew() {
        User op = newOperator();
        User gerente = newManager();
        CashRegister caixa = newOpenCashRegister(op);
        Product p = newProduct();
        Sale sale = saleService.openSale(op, caixa);
        sale = saleService.addItem(sale, p.getCode(), new BigDecimal("1"));
        sale = saleService.cancelSale(sale, "teste", gerente); // CANCELADA

        Sale result = saleService.resumeOrOpenSale(op, caixa, sale);

        assertNotSame(sale, result);
        assertEquals(Sale.Status.ABERTA, result.getStatus());
    }

    // 6. sessionSale de OUTRO operador (id diferente), mesmo caixa -> abre nova
    @Test
    void differentOperatorOpensNew() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        User other = userWithId(op.getId() + 777);
        Sale alheia = sessionSale(Sale.Status.ABERTA, other, caixa);

        Sale result = saleService.resumeOrOpenSale(op, caixa, alheia);

        assertNotSame(alheia, result);
        assertNotNull(result.getId());
        assertEquals(Sale.Status.ABERTA, result.getStatus());
        assertEquals(op.getId(), result.getOperator().getId());
    }

    // 7. sessionSale de OUTRO caixa (id diferente), mesmo operador -> abre nova
    @Test
    void differentCashRegisterOpensNew() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        CashRegister outroCaixa = cashRegisterWithId(caixa.getId() + 999);
        Sale alheia = sessionSale(Sale.Status.ABERTA, op, outroCaixa);

        Sale result = saleService.resumeOrOpenSale(op, caixa, alheia);

        assertNotSame(alheia, result);
        assertNotNull(result.getId());
        assertEquals(caixa.getId(), result.getCashRegister().getId());
    }

    // 8a. sessionSale.getOperator() == null -> abre nova (defensivo)
    @Test
    void nullOperatorInSessionOpensNew() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Sale semOperador = sessionSale(Sale.Status.ABERTA, null, caixa);

        Sale result = saleService.resumeOrOpenSale(op, caixa, semOperador);

        assertNotSame(semOperador, result);
        assertNotNull(result.getId());
        assertEquals(Sale.Status.ABERTA, result.getStatus());
    }

    // 8b. sessionSale.getCashRegister() == null -> abre nova (defensivo)
    @Test
    void nullCashRegisterInSessionOpensNew() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Sale semCaixa = sessionSale(Sale.Status.ABERTA, op, null);

        Sale result = saleService.resumeOrOpenSale(op, caixa, semCaixa);

        assertNotSame(semCaixa, result);
        assertNotNull(result.getId());
    }

    // 9. Mesmo id porém instâncias distintas de operador/caixa -> restaura (por id)
    @Test
    void sameIdDifferentInstancesResumes() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        // operador e caixa com o MESMO id, mas instâncias diferentes
        User opClone = userWithId(op.getId());
        CashRegister caixaClone = cashRegisterWithId(caixa.getId());
        Sale sessao = sessionSale(Sale.Status.ABERTA, opClone, caixaClone);

        Sale result = saleService.resumeOrOpenSale(op, caixa, sessao);

        // comparação por id, não por referência -> restaura a MESMA sessionSale
        assertSame(sessao, result);
    }

    // 10. operator com id null no lado da sessão -> abre nova
    @Test
    void nullIdInSessionOperatorOpensNew() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        User semId = new User(); // id null
        Sale sessao = sessionSale(Sale.Status.ABERTA, semId, caixa);

        Sale result = saleService.resumeOrOpenSale(op, caixa, sessao);

        assertNotSame(sessao, result);
        assertNotNull(result.getId());
        assertEquals(Sale.Status.ABERTA, result.getStatus());
    }

    // 11. Prova do bug: restaurar várias vezes NÃO duplica; fica 1 ABERTA no caixa.
    @Test
    void repeatedResumeDoesNotDuplicateOpenSale() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Sale current = saleService.openSale(op, caixa); // 1 ABERTA persistida

        for (int i = 0; i < 3; i++) {
            Sale resumed = saleService.resumeOrOpenSale(op, caixa, current);
            assertSame(current, resumed); // sempre a mesma, sem save de venda nova
            current = resumed;
        }

        // invariante: nenhuma órfã criada por navegação -> exatamente 1 ABERTA
        assertEquals(1, countOpenSalesFor(caixa));
    }
}
