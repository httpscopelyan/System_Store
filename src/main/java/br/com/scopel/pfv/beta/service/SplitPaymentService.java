package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.ElectronicPaymentReceivedException;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InvalidPaymentPartException;
import br.com.scopel.pfv.beta.exception.PaymentMethodNotAvailableException;
import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentInput;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SplitPaymentService {

    private static final String ELECTRONIC_PART_RECEIVED = "Esta venda já tem pagamento em cartão ou PIX recebido"
            + " na maquininha e não pode ser reaberta nem cancelada. Conclua o pagamento do valor restante.";
    private static final String ELECTRONIC_PART_NOT_REMOVABLE = "Parte em cartão ou PIX já foi cobrada na"
            + " maquininha e não pode ser removida.";

    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;
    private final SaleFinalizer saleFinalizer;
    private final AuditLogService auditLogService;

    public SplitPaymentService(SaleRepository saleRepository, PaymentRepository paymentRepository,
                               SaleFinalizer saleFinalizer, AuditLogService auditLogService) {
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
        this.saleFinalizer = saleFinalizer;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public SplitPaymentResult registerPart(Sale sale, PaymentInput part) {
        Sale saleInPayment = lockSaleInPayment(sale);
        BigDecimal partAmount = validatedAmount(part);
        CardType cardType = validatedCardType(part);
        BigDecimal remainingBeforePart = remainingAmountOf(saleInPayment);
        if (remainingBeforePart.signum() <= 0) {
            throw new InvalidPaymentPartException("Esta venda não tem valor restante a pagar.");
        }

        BigDecimal change = noChange();
        if (partAmount.compareTo(remainingBeforePart) > 0) {
            if (part.method() != PaymentMethod.DINHEIRO) {
                throw new InvalidPaymentPartException("O valor em cartão ou PIX não pode passar do restante ("
                        + formatMoney(remainingBeforePart) + "). Troco só sai de pagamento em dinheiro.");
            }
            change = partAmount.subtract(remainingBeforePart).setScale(2, RoundingMode.HALF_UP);
        }

        Payment registeredPart = savePart(saleInPayment, part.method(), cardType, partAmount, change);
        BigDecimal remainingAfterPart = remainingBeforePart.subtract(partAmount.subtract(change))
                .setScale(2, RoundingMode.HALF_UP);
        boolean finalized = remainingAfterPart.signum() == 0;
        Sale resultingSale = finalized ? saleFinalizer.finalizeSale(saleInPayment) : saleInPayment;
        Hibernate.initialize(resultingSale.getItems());
        return new SplitPaymentResult(resultingSale, registeredPart, remainingAfterPart, change, finalized);
    }

    @Transactional(readOnly = true)
    public SplitPaymentStatus status(Sale sale) {
        Sale persistedSale = saleRepository.findById(requireSaleId(sale))
                .orElseThrow(IllegalSaleStateException::new);
        return statusOf(persistedSale);
    }

    @Transactional(readOnly = true)
    public boolean hasRegisteredParts(Sale sale) {
        return sale != null && sale.getId() != null && paymentRepository.existsBySale_Id(sale.getId());
    }

    @Transactional(readOnly = true)
    public List<ReceiptPayment> receiptPayments(Sale sale) {
        return paymentRepository.findBySale_IdOrderByCreatedAtAscIdAsc(requireSaleId(sale)).stream()
                .map(ReceiptPayment::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Sale> findSaleWithPendingParts(User operator) {
        if (operator == null || operator.getId() == null) {
            return Optional.empty();
        }
        Optional<Sale> pendingSale = saleRepository
                .findWithRegisteredPayments(operator.getId(), Sale.Status.EM_PAGAMENTO).stream().findFirst();
        pendingSale.ifPresent(sale -> Hibernate.initialize(sale.getItems()));
        return pendingSale;
    }

    @Transactional
    public SplitPaymentStatus removeCashPart(Sale sale, Long paymentId) {
        Sale saleInPayment = lockSaleInPayment(sale);
        Payment part = paymentId == null ? null : paymentRepository.findById(paymentId).orElse(null);
        if (part == null || part.getSale() == null || !saleInPayment.getId().equals(part.getSale().getId())) {
            throw new InvalidPaymentPartException("Parte de pagamento não encontrada nesta venda.");
        }
        if (part.getMethod() != PaymentMethod.DINHEIRO) {
            throw new ElectronicPaymentReceivedException(ELECTRONIC_PART_NOT_REMOVABLE);
        }
        discard(saleInPayment, part);
        return statusOf(saleInPayment);
    }

    @Transactional
    public void discardCashPartsOrRefuse(Sale sale) {
        if (!hasRegisteredParts(sale)) {
            return;
        }
        Sale saleInPayment = lockSaleInPayment(sale);
        List<Payment> parts = paymentRepository.findBySale_IdOrderByCreatedAtAscIdAsc(saleInPayment.getId());
        if (parts.stream().anyMatch(part -> part.getMethod() != PaymentMethod.DINHEIRO)) {
            throw new ElectronicPaymentReceivedException(ELECTRONIC_PART_RECEIVED);
        }
        for (Payment cashPart : parts) {
            discard(saleInPayment, cashPart);
        }
    }

    private void discard(Sale sale, Payment cashPart) {
        paymentRepository.delete(cashPart);
        paymentRepository.flush();
        auditLogService.record(sale.getOperator(), AuditAction.PAYMENT_PART_REMOVED, AuditEntityType.SALE,
                sale.getId(), "Parte em dinheiro removida da venda #" + sale.getId(),
                formatMoney(cashPart.getAmountReceived()) + " (troco " + formatMoney(cashPart.getChange()) + ")",
                null);
    }

    private Sale lockSaleInPayment(Sale sale) {
        Sale persistedSale = saleRepository.findByIdForUpdate(requireSaleId(sale))
                .orElseThrow(IllegalSaleStateException::new);
        if (persistedSale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }
        return persistedSale;
    }

    private Long requireSaleId(Sale sale) {
        if (sale == null || sale.getId() == null) {
            throw new IllegalSaleStateException();
        }
        return sale.getId();
    }

    private BigDecimal validatedAmount(PaymentInput part) {
        if (part == null || part.method() == null) {
            throw new InvalidPaymentPartException("Informe a forma de pagamento da parte.");
        }
        if (part.method() == PaymentMethod.PRAZO) {
            throw new PaymentMethodNotAvailableException();
        }
        if (part.amount() == null) {
            throw new InvalidPaymentPartException("Informe o valor da parte do pagamento.");
        }
        BigDecimal partAmount = part.amount().setScale(2, RoundingMode.HALF_UP);
        if (partAmount.signum() <= 0) {
            throw new InvalidPaymentPartException("O valor da parte do pagamento deve ser maior que zero.");
        }
        return partAmount;
    }

    private CardType validatedCardType(PaymentInput part) {
        if (part.method() != PaymentMethod.CARTAO) {
            return null;
        }
        if (part.cardType() == null) {
            throw new InvalidPaymentPartException("Informe débito ou crédito.");
        }
        return part.cardType();
    }

    private Payment savePart(Sale sale, PaymentMethod method, CardType cardType, BigDecimal amountReceived,
                             BigDecimal change) {
        Payment payment = new Payment();
        payment.setSale(sale);
        payment.setMethod(method);
        payment.setCardType(cardType);
        payment.setAmountReceived(amountReceived);
        payment.setChange(change);
        payment.setCreatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    private SplitPaymentStatus statusOf(Sale sale) {
        List<Payment> parts = paymentRepository.findBySale_IdOrderByCreatedAtAscIdAsc(sale.getId());
        BigDecimal paidAmount = paidAmountOf(parts);
        BigDecimal remainingAmount = sale.getTotal().subtract(paidAmount).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return new SplitPaymentStatus(sale.getTotal(), paidAmount, remainingAmount, parts);
    }

    private BigDecimal remainingAmountOf(Sale sale) {
        return statusOf(sale).remainingAmount();
    }

    private BigDecimal paidAmountOf(List<Payment> parts) {
        BigDecimal paidAmount = BigDecimal.ZERO;
        for (Payment part : parts) {
            paidAmount = paidAmount.add(part.getAmountReceived().subtract(part.getChange()));
        }
        return paidAmount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal noChange() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal amount) {
        return "R$ " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
