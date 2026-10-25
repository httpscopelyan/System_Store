package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ChangeNotAvailableException;
import br.com.scopel.pfv.beta.exception.InsufficientPaymentException;
import br.com.scopel.pfv.beta.model.*;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.SaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PaymentMixedTests {

    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentRepository paymentRepository;

    private User operator() {
        User u = new User();
        u.setName("Op");
        u.setLogin("op_" + UUID.randomUUID());
        u.setPassword("x");
        u.setRole(User.Role.OPERADOR);
        u.setActive(true);
        return userRepository.save(u);
    }

    private Sale saleInPayment(User op, BigDecimal unitPrice, BigDecimal qty) {
        Product p = new Product();
        p.setCode(UUID.randomUUID().toString().substring(0, 13));
        p.setDescription("Produto");
        p.setPrice(unitPrice);
        p.setQuantity(new BigDecimal("100.000"));
        p.setUnit(Product.Unidade.UN);
        p.setActive(true);
        productRepository.save(p);

        CashRegister caixa = cashRegisterService.open(op, new BigDecimal("100.00"));
        Sale sale = saleService.openSale(op, caixa);
        sale = saleService.addItem(sale, p.getCode(), qty);
        return saleService.goToPayment(sale);
    }

    private long payments(Sale sale) {
        return paymentRepository.findAll().stream()
                .filter(pay -> pay.getSale().getId().equals(sale.getId()))
                .count();
    }

    @Test
    void splitCashAndPixFinalizes() {
        User op = operator();
        Sale sale = saleInPayment(op, new BigDecimal("10.00"), new BigDecimal("10")); // total 100.00

        sale = saleService.payMixed(sale, List.of(
                new PaymentInput(PaymentMethod.PIX, new BigDecimal("60.00")),
                new PaymentInput(PaymentMethod.DINHEIRO, new BigDecimal("40.00"))));

        assertEquals(Sale.Status.FINALIZADA, sale.getStatus());
        assertEquals(2, payments(sale));
    }

    @Test
    void sumBelowTotalThrows() {
        User op = operator();
        Sale sale = saleInPayment(op, new BigDecimal("10.00"), new BigDecimal("10")); // 100.00

        assertThrows(InsufficientPaymentException.class, () -> saleService.payMixed(sale, List.of(
                new PaymentInput(PaymentMethod.PIX, new BigDecimal("50.00")),
                new PaymentInput(PaymentMethod.CARTAO, new BigDecimal("40.00")))));
    }

    @Test
    void changeComesOnlyFromCash() {
        User op = operator();
        Sale sale = saleInPayment(op, new BigDecimal("10.00"), new BigDecimal("10")); // 100.00

        // cartão 90 + dinheiro 20 = 110; troco 10 precisa sair do dinheiro (20 >= 10) -> ok
        Sale paid = saleService.payMixed(sale, List.of(
                new PaymentInput(PaymentMethod.CARTAO, new BigDecimal("90.00")),
                new PaymentInput(PaymentMethod.DINHEIRO, new BigDecimal("20.00"))));

        Payment cashPayment = paymentRepository.findAll().stream()
                .filter(pay -> pay.getSale().getId().equals(paid.getId()))
                .filter(pay -> pay.getMethod() == PaymentMethod.DINHEIRO)
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("10.00").compareTo(cashPayment.getChange()));
    }

    @Test
    void overpayWithoutEnoughCashThrows() {
        User op = operator();
        Sale sale = saleInPayment(op, new BigDecimal("10.00"), new BigDecimal("10")); // 100.00

        // cartão 105 + dinheiro 0 => troco 5 sem dinheiro suficiente
        assertThrows(ChangeNotAvailableException.class, () -> saleService.payMixed(sale, List.of(
                new PaymentInput(PaymentMethod.CARTAO, new BigDecimal("105.00")))));
    }
}
