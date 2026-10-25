package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.FinancialOperationException;
import br.com.scopel.pfv.beta.exception.PayableNotFoundException;
import br.com.scopel.pfv.beta.model.Payable;
import br.com.scopel.pfv.beta.model.Purchase;
import br.com.scopel.pfv.beta.model.Supplier;
import br.com.scopel.pfv.beta.repository.PayableRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contas a Pagar (PDV Prático: CAP).
 */
@Service
public class PayableService {

    private final PayableRepository repository;

    public PayableService(PayableRepository repository) {
        this.repository = repository;
    }

    public Payable create(Supplier supplier, Purchase purchase, String description,
                          LocalDate dueDate, BigDecimal amount) {
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FinancialOperationException("valor da conta deve ser positivo");
        }
        Payable payable = new Payable();
        payable.setSupplier(supplier);
        payable.setPurchase(purchase);
        payable.setDescription(description);
        payable.setDueDate(dueDate);
        payable.setAmount(value);
        payable.setPaidAmount(BigDecimal.ZERO);
        payable.setStatus(Payable.Status.ABERTA);
        payable.setCreatedAt(LocalDateTime.now());
        return repository.save(payable);
    }

    @Transactional
    public Payable pay(Long payableId, BigDecimal value) {
        Payable p = repository.findById(payableId)
                .orElseThrow(() -> new PayableNotFoundException(payableId));
        if (p.getStatus() != Payable.Status.ABERTA) {
            throw new FinancialOperationException("conta não está aberta");
        }
        BigDecimal payment = value.setScale(2, RoundingMode.HALF_UP);
        if (payment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FinancialOperationException("valor pago deve ser positivo");
        }
        if (payment.compareTo(p.getRemaining()) > 0) {
            throw new FinancialOperationException("valor maior que o saldo da conta");
        }

        p.setPaidAmount(p.getPaidAmount().add(payment));
        if (p.getPaidAmount().compareTo(p.getAmount()) >= 0) {
            p.setStatus(Payable.Status.PAGA);
        }
        return repository.save(p);
    }

    public List<Payable> open() {
        return repository.findByStatus(Payable.Status.ABERTA);
    }

    public List<Payable> overdue(LocalDate asOf) {
        return repository.findByStatusAndDueDateLessThan(Payable.Status.ABERTA, asOf);
    }
}
