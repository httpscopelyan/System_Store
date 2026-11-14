package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SaleReprintService {

    private final SaleRepository saleRepository;
    private final SplitPaymentService splitPaymentService;
    private final ReceiptService receiptService;
    private final AuditLogService auditLogService;

    public SaleReprintService(SaleRepository saleRepository, SplitPaymentService splitPaymentService,
                              ReceiptService receiptService, AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
        this.saleRepository = saleRepository;
        this.splitPaymentService = splitPaymentService;
        this.receiptService = receiptService;
    }

    @Transactional
    public void printCopy(Long saleId) {
        Sale sale = saleId == null ? null : saleRepository.findById(saleId).orElse(null);
        if (sale == null || sale.getStatus() != Sale.Status.FINALIZADA) {
            throw new IllegalArgumentException("Venda finalizada não encontrada.");
        }
        List<ReceiptPayment> payments = splitPaymentService.receiptPayments(sale);
        receiptService.printSplitSaleReceiptCopy(sale, payments);
        auditLogService.record(null, AuditAction.RECEIPT_REPRINTED, AuditEntityType.SALE, sale.getId(),
                "2a via da venda #" + sale.getId() + " impressa", null, null);
    }
}
