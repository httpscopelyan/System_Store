package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SaleFinalizer {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final AuditLogService auditLogService;

    public SaleFinalizer(SaleRepository saleRepository, ProductRepository productRepository,
                         AuditLogService auditLogService) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Sale finalizeSale(Sale sale) {
        if (sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }
        decreaseStock(sale);
        sale.setStatus(Sale.Status.FINALIZADA);
        Sale finalized = saleRepository.save(sale);
        auditLogService.record(sale.getOperator(), AuditAction.SALE_FINALIZED, AuditEntityType.SALE, finalized.getId(),
                "Venda #" + finalized.getId() + " finalizada (" + sale.getItems().size() + " itens)", null,
                "R$ " + finalized.getTotal().toPlainString());
        return finalized;
    }

    private void decreaseStock(Sale sale) {
        for (SaleItem item : sale.getItems()) {
            Product product = item.getProduct();
            product.setQuantity(product.getQuantity().subtract(item.getQuantity()));
            productRepository.save(product);
        }
    }
}
