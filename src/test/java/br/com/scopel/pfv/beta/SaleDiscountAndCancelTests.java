package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InvalidDiscountException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.*;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.CustomerService;
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
class SaleDiscountAndCancelTests {

    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private CustomerService customerService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    private User user(User.Role role) {
        User u = new User();
        u.setName(role == User.Role.GERENTE ? "Gerente" : "Operador");
        u.setLogin("u_" + UUID.randomUUID());
        u.setPassword("x");
        u.setRole(role);
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
        p.setActive(true);
        return productRepository.save(p);
    }

    private Sale openWithItem(User operator, Product p, BigDecimal qty) {
        CashRegister caixa = cashRegisterService.open(operator, new BigDecimal("100.00"));
        Sale sale = saleService.openSale(operator, caixa);
        return saleService.addItem(sale, p.getCode(), qty);
    }

    // Desconto dentro do limite (10%) não precisa de gerente.
    @Test
    void saleDiscountWithinLimitNoManager() {
        User op = user(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("10")); // subtotal 100.00

        sale = saleService.applySaleDiscount(sale, new BigDecimal("5.00"), null); // 5%

        assertEquals(0, new BigDecimal("95.00").compareTo(sale.getTotal()));
    }

    // Desconto acima do limite exige gerente: operador é recusado.
    @Test
    void saleDiscountAboveLimitRequiresManager() {
        User op = user(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("10")); // subtotal 100.00

        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> saleService.applySaleDiscount(sale, new BigDecimal("20.00"), op)); // 20% com operador
    }

    // Mesmo desconto, com gerente autorizando, passa.
    @Test
    void saleDiscountAboveLimitWithManagerSucceeds() {
        User op = user(User.Role.OPERADOR);
        User gerente = user(User.Role.GERENTE);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("10"));

        sale = saleService.applySaleDiscount(sale, new BigDecimal("20.00"), gerente);

        assertEquals(0, new BigDecimal("80.00").compareTo(sale.getTotal()));
        assertEquals(gerente.getId(), sale.getAuthorizedBy().getId());
    }

    @Test
    void discountGreaterThanSubtotalIsInvalid() {
        User op = user(User.Role.OPERADOR);
        User gerente = user(User.Role.GERENTE);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("10")); // subtotal 100.00

        assertThrows(InvalidDiscountException.class,
                () -> saleService.applySaleDiscount(sale, new BigDecimal("150.00"), gerente));
    }

    @Test
    void itemDiscountWithinLimitReducesTotal() {
        User op = user(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("10")); // item gross 100.00

        sale = saleService.applyItemDiscount(sale, p.getCode(), new BigDecimal("8.00"), null); // 8%

        assertEquals(0, new BigDecimal("92.00").compareTo(sale.getTotal()));
        assertEquals(0, new BigDecimal("92.00").compareTo(sale.getItems().get(0).getTotalPrice()));
    }

    @Test
    void surchargeIncreasesTotal() {
        User op = user(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("10")); // 100.00

        sale = saleService.applySurcharge(sale, new BigDecimal("4.50"));

        assertEquals(0, new BigDecimal("104.50").compareTo(sale.getTotal()));
    }

    @Test
    void cancelItemRequiresManager() {
        User op = user(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("3"));

        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> saleService.cancelItem(sale, p.getCode(), op));
    }

    @Test
    void cancelItemByManagerRemovesItem() {
        User op = user(User.Role.OPERADOR);
        User gerente = user(User.Role.GERENTE);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("3")); // 30.00

        sale = saleService.cancelItem(sale, p.getCode(), gerente);

        assertTrue(sale.getItems().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(sale.getTotal()));
    }

    // RN17: cancelar venda mantém o registro com status, usuário e motivo.
    @Test
    void cancelSaleKeepsRecordWithReason() {
        User op = user(User.Role.OPERADOR);
        User gerente = user(User.Role.GERENTE);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("2"));

        sale = saleService.cancelSale(sale, "cliente desistiu", gerente);

        assertEquals(Sale.Status.CANCELADA, sale.getStatus());
        assertEquals("cliente desistiu", sale.getCancelReason());
        assertNotNull(sale.getCanceledAt());
        assertEquals(gerente.getId(), sale.getAuthorizedBy().getId());
        assertNotNull(sale.getId()); // registro preservado
    }

    @Test
    void cancelSaleRequiresManager() {
        User op = user(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("1"));

        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> saleService.cancelSale(sale, "motivo", op));
    }

    @Test
    void setCustomerAttachesToSale() {
        User op = user(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("1"));

        Customer c = new Customer();
        c.setName("Cliente Tele-entrega " + UUID.randomUUID());
        c = customerService.save(c);

        sale = saleService.setCustomer(sale, c);

        assertNotNull(sale.getCustomer());
        assertEquals(c.getId(), sale.getCustomer().getId());
    }

    // Guarda de estado: desconto de item fora de ABERTA é recusado.
    @Test
    void itemDiscountOnlyWhenOpen() {
        User op = user(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("1"));
        Sale inPayment = saleService.goToPayment(sale);

        assertThrows(IllegalSaleStateException.class,
                () -> saleService.applyItemDiscount(inPayment, p.getCode(), new BigDecimal("1.00"), null));
    }
}
