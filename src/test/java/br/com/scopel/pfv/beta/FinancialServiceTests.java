package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.FinancialOperationException;
import br.com.scopel.pfv.beta.model.Customer;
import br.com.scopel.pfv.beta.model.Payable;
import br.com.scopel.pfv.beta.model.Receivable;
import br.com.scopel.pfv.beta.service.CustomerService;
import br.com.scopel.pfv.beta.service.PayableService;
import br.com.scopel.pfv.beta.service.ReceivableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FinancialServiceTests {

    @Autowired private ReceivableService receivableService;
    @Autowired private PayableService payableService;
    @Autowired private CustomerService customerService;

    private Customer customer() {
        Customer c = new Customer();
        c.setName("Cliente " + UUID.randomUUID());
        c.setCreditLimit(new BigDecimal("1000.00"));
        return customerService.save(c);
    }

    @Test
    void installmentsSplitAndLastAbsorbsRemainder() {
        Customer c = customer();

        List<Receivable> parcelas = receivableService.createInstallments(
                c, null, new BigDecimal("100.00"), 3, LocalDate.now(), 30);

        assertEquals(3, parcelas.size());
        assertEquals(0, new BigDecimal("33.33").compareTo(parcelas.get(0).getAmount()));
        assertEquals(0, new BigDecimal("33.34").compareTo(parcelas.get(2).getAmount()));
        BigDecimal soma = parcelas.stream().map(Receivable::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100.00").compareTo(soma));
    }

    @Test
    void receivePartialThenFullQuitaTitulo() {
        Customer c = customer();
        Receivable r = receivableService.createInstallments(
                c, null, new BigDecimal("50.00"), 1, LocalDate.now(), 30).get(0);

        r = receivableService.receive(r.getId(), new BigDecimal("20.00"));
        assertEquals(Receivable.Status.ABERTA, r.getStatus());
        assertEquals(0, new BigDecimal("30.00").compareTo(r.getRemaining()));

        r = receivableService.receive(r.getId(), new BigDecimal("30.00"));
        assertEquals(Receivable.Status.PAGA, r.getStatus());
    }

    @Test
    void receiveMoreThanBalanceThrows() {
        Customer c = customer();
        Receivable r = receivableService.createInstallments(
                c, null, new BigDecimal("50.00"), 1, LocalDate.now(), 30).get(0);

        assertThrows(FinancialOperationException.class,
                () -> receivableService.receive(r.getId(), new BigDecimal("60.00")));
    }

    @Test
    void customerDebtSumsOpenBalances() {
        Customer c = customer();
        receivableService.createInstallments(c, null, new BigDecimal("90.00"), 3, LocalDate.now(), 30);

        assertEquals(0, new BigDecimal("90.00").compareTo(receivableService.customerDebt(c.getId())));
    }

    @Test
    void overdueDetectedWhenDueDatePast() {
        Customer c = customer();
        receivableService.createInstallments(c, null, new BigDecimal("40.00"), 1, LocalDate.now().minusDays(5), 30);

        assertTrue(receivableService.hasOverdue(c.getId(), LocalDate.now()));
    }

    @Test
    void renegotiateCancelsOldAndCreatesNewForBalance() {
        Customer c = customer();
        List<Receivable> original = receivableService.createInstallments(
                c, null, new BigDecimal("100.00"), 2, LocalDate.now(), 30);
        // paga 40 na primeira -> saldo total 60
        receivableService.receive(original.get(0).getId(), new BigDecimal("40.00"));

        List<Receivable> novas = receivableService.renegotiate(c, 3, LocalDate.now().plusDays(10), 30);

        assertEquals(3, novas.size());
        assertEquals(0, new BigDecimal("60.00").compareTo(receivableService.customerDebt(c.getId())));
    }

    @Test
    void payableCreateAndPay() {
        Payable p = payableService.create(null, null, "Energia", LocalDate.now().plusDays(10),
                new BigDecimal("150.00"));
        assertEquals(Payable.Status.ABERTA, p.getStatus());

        p = payableService.pay(p.getId(), new BigDecimal("150.00"));
        assertEquals(Payable.Status.PAGA, p.getStatus());
    }
}
