package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.*;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ReportService;
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

@SpringBootTest
@Transactional
class ReportServiceTests {

    @Autowired private ReportService reportService;
    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    private final LocalDateTime start = LocalDateTime.now().minusMinutes(5);
    private final LocalDateTime end = LocalDateTime.now().plusMinutes(5);

    private void finalizedSale() {
        User op = new User();
        op.setName("Op");
        op.setLogin("op_" + UUID.randomUUID());
        op.setPassword("x");
        op.setRole(User.Role.OPERADOR);
        op.setActive(true);
        op = userRepository.save(op);

        Product p = new Product();
        p.setCode(UUID.randomUUID().toString().substring(0, 13));
        p.setDescription("Produto Relatorio");
        p.setPrice(new BigDecimal("10.00"));
        p.setCostPrice(new BigDecimal("6.00"));
        p.setQuantity(new BigDecimal("100.000"));
        p.setUnit(Product.Unidade.UN);
        productRepository.save(p);

        CashRegister caixa = cashRegisterService.open(op, new BigDecimal("100.00"));
        Sale sale = saleService.openSale(op, caixa);
        sale = saleService.addItem(sale, p.getCode(), new BigDecimal("2")); // total 20.00
        sale = saleService.goToPayment(sale);
        saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal("20.00"));
    }

    @Test
    void summaryAndProfit() {
        finalizedSale();

        ReportService.SalesSummary s = reportService.summary(start, end);

        assertTrue(s.totalSales().compareTo(new BigDecimal("20.00")) >= 0);
        // lucro do item: 20 - 6*2 = 8 (pode somar com outros testes no mesmo período? @Transactional isola)
        assertTrue(s.grossProfit().compareTo(new BigDecimal("8.00")) >= 0);
    }

    @Test
    void byPaymentMethodHasCash() {
        finalizedSale();

        List<ReportService.NamedAmount> methods = reportService.byPaymentMethod(start, end);

        assertTrue(methods.stream().anyMatch(m -> m.name().equals("DINHEIRO")
                && m.amount().compareTo(BigDecimal.ZERO) > 0));
    }

    @Test
    void topProductsListsSoldItem() {
        finalizedSale();

        List<ReportService.ProductQuantity> top = reportService.topProducts(start, end, 10);

        assertTrue(top.stream().anyMatch(t -> t.description().equals("Produto Relatorio")));
    }
}
