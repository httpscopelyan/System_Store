package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InvalidQuantityException;
import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.CashRegister;
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

/**
 * 3ª leva — valida no nível de service:
 * ITEM 1: {@link SaleService#reopenForEditing} (RN21: EM_PAGAMENTO -> ABERTA),
 *         incluindo ausência de Payment órfão e ciclo repetível.
 * ITEM 2: produto do cadastro rápido via {@link ProductService#save} + rebipe
 *         (código String com zeros à esquerda, quantity=0, active=true, unit,
 *         RN19 preço congelado, RN12 estoque negativo permitido, duplicidade).
 *
 * O modal QuickProductController e PaymentController.onVoltar são UI (não aqui);
 * aqui replicamos o que eles mandam ao service — buildProduct(code, qty=0, active,
 * unit) + save + addItem — que é o que precisa de garantia.
 */
@SpringBootTest
@Transactional
class ReopenAndQuickProductTests {

    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentRepository paymentRepository;

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

    private Product newProduct(BigDecimal price, BigDecimal stock, Product.Unidade unit) {
        Product p = new Product();
        p.setCode(UUID.randomUUID().toString().substring(0, 13));
        p.setDescription("Produto");
        p.setPrice(price);
        p.setQuantity(stock);
        p.setUnit(unit);
        p.setActive(true);
        return productRepository.save(p);
    }

    /** Replica QuickProductController.buildProduct: code cru, quantity=0, active=true. */
    private Product buildQuickProduct(String code, BigDecimal price, Product.Unidade unit) {
        Product p = new Product();
        p.setCode(code);
        p.setDescription("Cadastro rapido");
        p.setPrice(price);
        p.setUnit(unit);
        p.setQuantity(BigDecimal.ZERO);
        p.setActive(true);
        return p;
    }

    private long countPaymentsForSale(Sale sale) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getSale().getId().equals(sale.getId()))
                .count();
    }

    // ===================== ITEM 1 — reopenForEditing =====================

    // 1. EM_PAGAMENTO -> ABERTA (persistida); itens/total/desconto/authorizedBy preservados
    @Test
    void reopenFromEmPagamentoBecomesAbertaPreservingState() {
        User op = newOperator();
        User gerente = newManager();
        CashRegister caixa = newOpenCashRegister(op);
        Sale sale = saleService.openSale(op, caixa);

        Product product = newProduct(new BigDecimal("50.00"), new BigDecimal("100.000"), Product.Unidade.UN);
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("2")); // subtotal 100.00

        // desconto de 20.00 (20% > limite 10%) exige gerente -> grava authorizedBy
        sale = saleService.applySaleDiscount(sale, new BigDecimal("20.00"), gerente);
        assertNotNull(sale.getAuthorizedBy());
        Long authorizedId = sale.getAuthorizedBy().getId();
        BigDecimal totalAntes = sale.getTotal();          // 100 - 20 = 80.00
        BigDecimal descontoAntes = sale.getDiscount();    // 20.00

        sale = saleService.goToPayment(sale);
        assertEquals(Sale.Status.EM_PAGAMENTO, sale.getStatus());

        Sale reopened = saleService.reopenForEditing(sale);

        assertEquals(Sale.Status.ABERTA, reopened.getStatus());
        assertNotNull(reopened.getId());
        assertEquals(1, reopened.getItems().size());
        assertEquals(0, totalAntes.compareTo(reopened.getTotal()));
        assertEquals(0, descontoAntes.compareTo(reopened.getDiscount()));
        assertNotNull(reopened.getAuthorizedBy());
        assertEquals(authorizedId, reopened.getAuthorizedBy().getId());
    }

    // 2. ABERTA -> IllegalSaleStateException
    @Test
    void reopenFromAbertaThrows() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Sale sale = saleService.openSale(op, caixa);

        assertThrows(IllegalSaleStateException.class, () -> saleService.reopenForEditing(sale));
    }

    // 3. FINALIZADA -> IllegalSaleStateException
    @Test
    void reopenFromFinalizadaThrows() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Sale sale = saleService.openSale(op, caixa);
        Product product = newProduct(new BigDecimal("5.00"), new BigDecimal("50.000"), Product.Unidade.UN);
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("1"));
        sale = saleService.goToPayment(sale);
        final Sale finalized = saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal("5.00"));

        assertEquals(Sale.Status.FINALIZADA, finalized.getStatus());
        assertThrows(IllegalSaleStateException.class, () -> saleService.reopenForEditing(finalized));
    }

    // 4. CANCELADA -> IllegalSaleStateException
    @Test
    void reopenFromCanceladaThrows() {
        User op = newOperator();
        User gerente = newManager();
        CashRegister caixa = newOpenCashRegister(op);
        Sale sale = saleService.openSale(op, caixa);
        Product product = newProduct(new BigDecimal("5.00"), new BigDecimal("50.000"), Product.Unidade.UN);
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("1"));
        final Sale canceled = saleService.cancelSale(sale, "teste", gerente);

        assertEquals(Sale.Status.CANCELADA, canceled.getStatus());
        assertThrows(IllegalSaleStateException.class, () -> saleService.reopenForEditing(canceled));
    }

    // 5. Nenhum Payment órfão após goToPayment -> reopenForEditing
    @Test
    void noOrphanPaymentAfterReopenCycle() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Sale sale = saleService.openSale(op, caixa);
        Product product = newProduct(new BigDecimal("7.00"), new BigDecimal("50.000"), Product.Unidade.UN);
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("1"));

        sale = saleService.goToPayment(sale);
        sale = saleService.reopenForEditing(sale);

        // EM_PAGAMENTO não grava Payment -> reverter não deixa Payment órfão
        assertEquals(0, countPaymentsForSale(sale));
    }

    // 6. Ciclo repetível: ABERTA -> pagamento -> reopen -> addItem -> pagamento de novo
    @Test
    void reopenCycleIsRepeatableAndAllowsAddItemAgain() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Sale sale = saleService.openSale(op, caixa);
        Product product = newProduct(new BigDecimal("3.00"), new BigDecimal("50.000"), Product.Unidade.UN);

        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("1"));
        sale = saleService.goToPayment(sale);                 // EM_PAGAMENTO
        sale = saleService.reopenForEditing(sale);            // volta a ABERTA
        assertEquals(Sale.Status.ABERTA, sale.getStatus());

        // addItem volta a funcionar após reabrir
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("2"));
        assertEquals(1, sale.getItems().size());
        assertEquals(0, new BigDecimal("3").compareTo(sale.getItems().get(0).getQuantity()));
        assertEquals(0, new BigDecimal("9.00").compareTo(sale.getTotal()));

        // e goToPayment de novo funciona
        sale = saleService.goToPayment(sale);
        assertEquals(Sale.Status.EM_PAGAMENTO, sale.getStatus());
        assertEquals(0, countPaymentsForSale(sale));
    }

    // ===================== ITEM 2 — cadastro rápido via save + rebipe =====================

    // 7. Código String com zeros à esquerda recuperável IDÊNTICO (sem parseLong)
    @Test
    void quickProductKeepsLeadingZerosInCode() {
        String code = "007891000000017"; // 15 chars, zeros à esquerda
        Product saved = productService.save(buildQuickProduct(code, new BigDecimal("4.50"), Product.Unidade.UN));

        assertNotNull(saved.getId());
        Product found = productService.findCode(code);
        assertEquals(code, found.getCode());                 // idêntico, zeros preservados
        assertEquals(0, new BigDecimal("4.50").compareTo(found.getPrice()));
        assertTrue(found.getActive());
        assertEquals(0, BigDecimal.ZERO.compareTo(found.getQuantity()));
    }

    // 8. Produto novo quantity=0, active=true, UN -> addItem 1 (RN19), estoque vai a negativo (RN12 permitido, venda não quebra)
    @Test
    void quickProductUnAddItemFreezesPriceAndAllowsNegativeStock() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Sale sale = saleService.openSale(op, caixa);

        String code = "000111222333444";
        Product saved = productService.save(buildQuickProduct(code, new BigDecimal("9.90"), Product.Unidade.UN));
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getQuantity()));
        assertTrue(saved.getActive());

        // rebipe: adiciona 1 UN
        sale = saleService.addItem(sale, code, new BigDecimal("1"));
        SaleItem item = sale.getItems().get(0);
        // RN19: unitPrice congelado == preço cadastrado
        assertEquals(0, new BigDecimal("9.90").compareTo(item.getUnitPrice()));
        assertEquals(0, new BigDecimal("9.90").compareTo(sale.getTotal()));

        // finaliza: estoque 0 - 1 = -1 (RN12 permitido) e a venda NÃO quebra
        sale = saleService.goToPayment(sale);
        sale = saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal("10.00"));
        assertEquals(Sale.Status.FINALIZADA, sale.getStatus());

        Product reloaded = productService.findCode(code);
        assertEquals(0, new BigDecimal("-1.000").compareTo(reloaded.getQuantity()));
    }

    // 9. Produto KG aceita fração; o mesmo valor em UN seria recusado (a unidade escolhida importa)
    @Test
    void quickProductKgAcceptsFractionWhileUnWouldReject() {
        User op = newOperator();
        CashRegister caixa = newOpenCashRegister(op);
        Sale sale = saleService.openSale(op, caixa);

        String kgCode = "020000000000005";
        productService.save(buildQuickProduct(kgCode, new BigDecimal("12.00"), Product.Unidade.KG));

        // KG: 1.250 passa em validateQuantity (RN05)
        sale = saleService.addItem(sale, kgCode, new BigDecimal("1.250"));
        SaleItem item = sale.getItems().get(0);
        assertEquals(0, new BigDecimal("1.250").compareTo(item.getQuantity()));
        assertEquals(3, item.getQuantity().scale());
        // 1.250 * 12.00 = 15.00
        assertEquals(0, new BigDecimal("15.00").compareTo(item.getTotalPrice()));

        // o MESMO valor fracionário num produto UN é recusado -> prova que a unidade importa
        String unCode = "030000000000006";
        productService.save(buildQuickProduct(unCode, new BigDecimal("12.00"), Product.Unidade.UN));
        final Sale saleRef = sale;
        assertThrows(InvalidQuantityException.class,
                () -> saleService.addItem(saleRef, unCode, new BigDecimal("1.250")));
    }

    // 10. save com código já existente -> RegisterProductDuplicateException
    @Test
    void quickProductWithDuplicateCodeThrows() {
        String code = "099988877766655";
        productService.save(buildQuickProduct(code, new BigDecimal("1.00"), Product.Unidade.UN));

        assertThrows(RegisterProductDuplicateException.class,
                () -> productService.save(buildQuickProduct(code, new BigDecimal("2.00"), Product.Unidade.KG)));
    }
}
