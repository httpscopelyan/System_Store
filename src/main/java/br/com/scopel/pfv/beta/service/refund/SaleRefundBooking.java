package br.com.scopel.pfv.beta.service.refund;

import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.model.StockMovement;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import br.com.scopel.pfv.beta.repository.StockMovementRepository;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class SaleRefundBooking {

    static final String REASON_PREFIX = "Reembolso: ";
    private static final int MAX_CANCEL_REASON = 200;

    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final AuditLogService auditLogService;

    public SaleRefundBooking(SaleRepository saleRepository, PaymentRepository paymentRepository,
                             ProductRepository productRepository, StockMovementRepository movementRepository,
                             AuditLogService auditLogService) {
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPaymentRefunded(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(IllegalSaleStateException::new);
        if (payment.getRefundedAt() == null) {
            payment.setRefundedAt(LocalDateTime.now());
            paymentRepository.save(payment);
        }
    }

    @Transactional
    public Sale complete(Long saleId, String reason, User manager) {
        Sale sale = saleRepository.findByIdForUpdate(saleId).orElseThrow(IllegalSaleStateException::new);
        if (sale.getStatus() != Sale.Status.FINALIZADA) {
            throw new IllegalSaleStateException();
        }
        LocalDateTime now = LocalDateTime.now();
        returnItemsToStock(sale, now, manager);
        for (Payment payment : paymentRepository.findBySale_IdOrderByCreatedAtAscIdAsc(saleId)) {
            if (payment.getRefundedAt() == null) {
                payment.setRefundedAt(now);
                paymentRepository.save(payment);
            }
        }
        sale.setStatus(Sale.Status.CANCELADA);
        sale.setCanceledAt(now);
        sale.setRefundedAt(now);
        sale.setCancelReason(clip(REASON_PREFIX + reason, MAX_CANCEL_REASON));
        sale.setAuthorizedBy(manager);
        Sale refunded = saleRepository.save(sale);
        auditLogService.record(manager, AuditAction.SALE_REFUNDED, AuditEntityType.SALE, saleId,
                "Venda #" + saleId + " reembolsada: " + reason, null, "R$ " + sale.getTotal().toPlainString());
        return refunded;
    }

    private void returnItemsToStock(Sale sale, LocalDateTime when, User manager) {
        for (SaleItem item : sale.getItems()) {
            Product product = item.getProduct();
            BigDecimal restored = product.getQuantity().add(item.getQuantity());
            product.setQuantity(restored);
            productRepository.save(product);
            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setType(StockMovement.Type.ENTRADA);
            movement.setQuantity(item.getQuantity());
            movement.setReason("Reembolso da venda #" + sale.getId());
            movement.setOperator(manager);
            movement.setCreatedAt(when);
            movement.setResultingStock(restored);
            movementRepository.save(movement);
        }
    }

    private String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
