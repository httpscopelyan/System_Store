package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.IllegalQuoteStateException;
import br.com.scopel.pfv.beta.model.*;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.QuoteService;
import br.com.scopel.pfv.beta.service.SaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class QuoteServiceTests {

    @Autowired private QuoteService quoteService;
    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    private User operator() {
        User u = new User();
        u.setName("Op");
        u.setLogin("op_" + UUID.randomUUID());
        u.setPassword("x");
        u.setRole(User.Role.OPERADOR);
        u.setActive(true);
        return userRepository.save(u);
    }

    private Product product(BigDecimal price) {
        Product p = new Product();
        p.setCode(UUID.randomUUID().toString().substring(0, 13));
        p.setDescription("Produto");
        p.setPrice(price);
        p.setQuantity(new BigDecimal("100.000"));
        p.setUnit(Product.Unidade.UN);
        return productRepository.save(p);
    }

    @Test
    void addItemComputesTotal() {
        Quote q = quoteService.openQuote(null, operator(), LocalDate.now().plusDays(7));
        Product p = product(new BigDecimal("10.00"));

        q = quoteService.addItem(q, p.getCode(), new BigDecimal("3"));

        assertEquals(0, new BigDecimal("30.00").compareTo(q.getTotal()));
    }

    @Test
    void convertToSaleCreatesOpenSaleWithSameItems() {
        User op = operator();
        Quote q = quoteService.openQuote(null, op, LocalDate.now().plusDays(7));
        Product p1 = product(new BigDecimal("10.00"));
        Product p2 = product(new BigDecimal("5.00"));
        q = quoteService.addItem(q, p1.getCode(), new BigDecimal("2")); // 20
        q = quoteService.addItem(q, p2.getCode(), new BigDecimal("4")); // 20

        CashRegister caixa = cashRegisterService.open(op, new BigDecimal("100.00"));
        Sale sale = quoteService.convertToSale(q, caixa, op);

        assertEquals(Sale.Status.ABERTA, sale.getStatus());
        assertEquals(2, sale.getItems().size());
        assertEquals(0, new BigDecimal("40.00").compareTo(sale.getTotal()));
        assertEquals(Quote.Status.CONVERTIDO, quoteService.findById(q.getId()).getStatus());
    }

    @Test
    void cannotConvertTwice() {
        User op = operator();
        Quote q = quoteService.openQuote(null, op, LocalDate.now().plusDays(7));
        Product p = product(new BigDecimal("10.00"));
        q = quoteService.addItem(q, p.getCode(), new BigDecimal("1"));
        CashRegister caixa = cashRegisterService.open(op, new BigDecimal("100.00"));
        quoteService.convertToSale(q, caixa, op);

        final Quote converted = quoteService.findById(q.getId());
        assertThrows(IllegalQuoteStateException.class,
                () -> quoteService.convertToSale(converted, caixa, op));
    }
}
