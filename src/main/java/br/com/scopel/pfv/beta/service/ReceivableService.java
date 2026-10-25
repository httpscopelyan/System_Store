package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.FinancialOperationException;
import br.com.scopel.pfv.beta.exception.ReceivableNotFoundException;
import br.com.scopel.pfv.beta.model.Customer;
import br.com.scopel.pfv.beta.model.Receivable;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.repository.ReceivableRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Contas a Receber / crediário (PDV Prático: CAR, dívida do cliente, parcelas).
 */
@Service
public class ReceivableService {

    private final ReceivableRepository repository;

    public ReceivableService(ReceivableRepository repository) {
        this.repository = repository;
    }

    /**
     * Gera N parcelas de um valor total. A divisão arredonda para baixo e a
     * última parcela absorve a sobra, de modo que a soma bate exatamente com o total.
     */
    @Transactional
    public List<Receivable> createInstallments(Customer customer, Sale sale, BigDecimal total,
                                               int installments, LocalDate firstDueDate, int intervalDays) {
        if (installments < 1) {
            throw new FinancialOperationException("número de parcelas inválido");
        }
        BigDecimal amount = total.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FinancialOperationException("valor a parcelar deve ser positivo");
        }

        BigDecimal base = amount.divide(new BigDecimal(installments), 2, RoundingMode.FLOOR);
        BigDecimal allocatedBeforeLast = base.multiply(new BigDecimal(installments - 1));
        BigDecimal last = amount.subtract(allocatedBeforeLast);

        List<Receivable> result = new ArrayList<>();
        for (int i = 1; i <= installments; i++) {
            Receivable r = new Receivable();
            r.setCustomer(customer);
            r.setSale(sale);
            r.setDescription("Parcela " + i + "/" + installments
                    + (sale != null ? " - Venda #" + sale.getId() : ""));
            r.setDueDate(firstDueDate.plusDays((long) intervalDays * (i - 1)));
            r.setAmount(i < installments ? base : last);
            r.setPaidAmount(BigDecimal.ZERO);
            r.setStatus(Receivable.Status.ABERTA);
            r.setInstallmentNumber(i);
            r.setInstallmentTotal(installments);
            r.setCreatedAt(LocalDateTime.now());
            result.add(repository.save(r));
        }
        return result;
    }

    /** Recebe (total ou parcial) um título. Quita quando o pago alcança o valor. */
    @Transactional
    public Receivable receive(Long receivableId, BigDecimal value) {
        Receivable r = repository.findById(receivableId)
                .orElseThrow(() -> new ReceivableNotFoundException(receivableId));
        if (r.getStatus() != Receivable.Status.ABERTA) {
            throw new FinancialOperationException("título não está aberto");
        }
        BigDecimal payment = value.setScale(2, RoundingMode.HALF_UP);
        if (payment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FinancialOperationException("valor recebido deve ser positivo");
        }
        if (payment.compareTo(r.getRemaining()) > 0) {
            throw new FinancialOperationException("valor maior que o saldo do título");
        }

        r.setPaidAmount(r.getPaidAmount().add(payment));
        if (r.getPaidAmount().compareTo(r.getAmount()) >= 0) {
            r.setStatus(Receivable.Status.PAGA);
        }
        return repository.save(r);
    }

    /** Dívida atual do cliente: soma dos saldos em aberto. */
    public BigDecimal customerDebt(Long customerId) {
        BigDecimal debt = BigDecimal.ZERO;
        for (Receivable r : repository.findByCustomer_IdAndStatus(customerId, Receivable.Status.ABERTA)) {
            debt = debt.add(r.getRemaining());
        }
        return debt.setScale(2, RoundingMode.HALF_UP);
    }

    public List<Receivable> openByCustomer(Long customerId) {
        return repository.findByCustomer_IdAndStatus(customerId, Receivable.Status.ABERTA);
    }

    public List<Receivable> overdue(Long customerId, LocalDate asOf) {
        return repository.findByCustomer_IdAndStatusAndDueDateLessThan(
                customerId, Receivable.Status.ABERTA, asOf);
    }

    public boolean hasOverdue(Long customerId, LocalDate asOf) {
        return !overdue(customerId, asOf).isEmpty();
    }

    /**
     * Renegocia (PDV Prático: Redefinir Parcelamento): cancela os títulos em
     * aberto do cliente e cria novas parcelas para o saldo restante.
     */
    @Transactional
    public List<Receivable> renegotiate(Customer customer, int newInstallments,
                                        LocalDate firstDueDate, int intervalDays) {
        List<Receivable> open = openByCustomer(customer.getId());
        if (open.isEmpty()) {
            throw new FinancialOperationException("cliente não tem títulos em aberto");
        }
        BigDecimal remaining = BigDecimal.ZERO;
        for (Receivable r : open) {
            remaining = remaining.add(r.getRemaining());
            r.setStatus(Receivable.Status.CANCELADA);
            repository.save(r);
        }
        return createInstallments(customer, null, remaining, newInstallments, firstDueDate, intervalDays);
    }
}
