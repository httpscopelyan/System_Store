package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.*;
import br.com.scopel.pfv.beta.repository.ProductRepository;
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
 * Garante que o saldo esperado do caixa (e a diferença no fechamento) inclui o
 * dinheiro recebido nas vendas.
 */
@SpringBootTest
@Transactional
class CashRegisterSalesTests {

    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private SaleService saleService;
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

    @Test
    void cashSaleIncreasesExpectedBalanceAndClosesWithZeroDifference() {
        User op = operator();
        CashRegister caixa = cashRegisterService.open(op, new BigDecimal("100.00"));

        Product p = new Product();
        p.setCode(UUID.randomUUID().toString().substring(0, 13));
        p.setDescription("Produto caixa");
        p.setPrice(new BigDecimal("10.00"));
        p.setQuantity(new BigDecimal("100.000"));
        p.setUnit(Product.Unidade.UN);
        productRepository.save(p);

        Sale sale = saleService.openSale(op, caixa);
        sale = saleService.addItem(sale, p.getCode(), new BigDecimal("2")); // total 20,00
        sale = saleService.goToPayment(sale);
        saleService.pay(sale, PaymentMethod.DINHEIRO, new BigDecimal("20.00")); // sem troco

        // Saldo esperado = fundo 100 + dinheiro das vendas 20 = 120
        assertEquals(0, new BigDecimal("120.00").compareTo(cashRegisterService.currentBalance(caixa)));

        // Fecha contando 120 -> diferença 0
        CashRegister fechado = cashRegisterService.close(caixa.getId(), new BigDecimal("120.00"));
        assertEquals(CashRegister.Status.CLOSED, fechado.getStatus());
        assertEquals(0, new BigDecimal("120.00").compareTo(fechado.getExpectedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fechado.getDifference()));
    }
}
