package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.CreditLimitExceededException;
import br.com.scopel.pfv.beta.exception.CustomerCreditBlockedException;
import br.com.scopel.pfv.beta.exception.FinancialOperationException;
import br.com.scopel.pfv.beta.model.*;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.*;
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
class CreditSaleServiceTests {

    @Autowired private CreditSaleService creditSaleService;
    @Autowired private ReceivableService receivableService;
    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private CustomerService customerService;
    @Autowired private AppSettingService settings;
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

    private Customer customer(BigDecimal limit) {
        Customer c = new Customer();
        c.setName("Cliente " + UUID.randomUUID());
        c.setCreditLimit(limit);
        return customerService.save(c);
    }

    private Sale saleInPaymentFor(Customer c, BigDecimal unit, BigDecimal qty) {
        User op = operator();
        Product p = new Product();
        p.setCode(UUID.randomUUID().toString().substring(0, 13));
        p.setDescription("Produto");
        p.setPrice(unit);
        p.setQuantity(new BigDecimal("100.000"));
        p.setUnit(Product.Unidade.UN);
        p.setActive(true);
        productRepository.save(p);

        CashRegister caixa = cashRegisterService.open(op, new BigDecimal("100.00"));
        Sale sale = saleService.openSale(op, caixa);
        sale = saleService.setCustomer(sale, c);
        sale = saleService.addItem(sale, p.getCode(), qty);
        return saleService.goToPayment(sale);
    }

    @Test
    void creditSaleGeneratesInstallmentsAndDebt() {
        settings.set(AppSettingService.CREDIT_ALLOW_CREDIT_SALE, "true");
        Customer c = customer(new BigDecimal("1000.00"));
        Sale sale = saleInPaymentFor(c, new BigDecimal("10.00"), new BigDecimal("9")); // 90.00

        Sale finalized = creditSaleService.sellOnCredit(sale, 3, LocalDate.now().plusDays(30), 30);

        assertEquals(Sale.Status.FINALIZADA, finalized.getStatus());
        assertEquals(3, receivableService.openByCustomer(c.getId()).size());
        assertEquals(0, new BigDecimal("90.00").compareTo(receivableService.customerDebt(c.getId())));
    }

    @Test
    void creditDisabledThrows() {
        settings.set(AppSettingService.CREDIT_ALLOW_CREDIT_SALE, "false");
        Customer c = customer(new BigDecimal("1000.00"));
        Sale sale = saleInPaymentFor(c, new BigDecimal("10.00"), new BigDecimal("1"));

        assertThrows(FinancialOperationException.class,
                () -> creditSaleService.sellOnCredit(sale, 1, LocalDate.now().plusDays(30), 30));
    }

    @Test
    void blockedCustomerThrows() {
        settings.set(AppSettingService.CREDIT_ALLOW_CREDIT_SALE, "true");
        Customer c = customer(new BigDecimal("1000.00"));
        customerService.setCreditBlocked(c.getId(), true);
        Sale sale = saleInPaymentFor(c, new BigDecimal("10.00"), new BigDecimal("1"));

        assertThrows(CustomerCreditBlockedException.class,
                () -> creditSaleService.sellOnCredit(sale, 1, LocalDate.now().plusDays(30), 30));
    }

    @Test
    void overdueCustomerIsBlocked() {
        settings.set(AppSettingService.CREDIT_ALLOW_CREDIT_SALE, "true");
        settings.set(AppSettingService.CREDIT_BLOCK_OVERDUE, "true");
        Customer c = customer(new BigDecimal("1000.00"));
        // título vencido
        receivableService.createInstallments(c, null, new BigDecimal("30.00"), 1, LocalDate.now().minusDays(3), 30);
        Sale sale = saleInPaymentFor(c, new BigDecimal("10.00"), new BigDecimal("1"));

        assertThrows(CustomerCreditBlockedException.class,
                () -> creditSaleService.sellOnCredit(sale, 1, LocalDate.now().plusDays(30), 30));
    }

    @Test
    void overLimitThrows() {
        settings.set(AppSettingService.CREDIT_ALLOW_CREDIT_SALE, "true");
        Customer c = customer(new BigDecimal("50.00"));                 // limite baixo
        Sale sale = saleInPaymentFor(c, new BigDecimal("10.00"), new BigDecimal("9")); // 90.00 > 50

        assertThrows(CreditLimitExceededException.class,
                () -> creditSaleService.sellOnCredit(sale, 1, LocalDate.now().plusDays(30), 30));
    }
}
