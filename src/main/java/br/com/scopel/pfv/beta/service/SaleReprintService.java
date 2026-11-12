package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SaleReprintService {

    private final SaleRepository saleRepository;
    private final SplitPaymentService splitPaymentService;
    private final ReceiptService receiptService;

    public SaleReprintService(SaleRepository saleRepository, SplitPaymentService splitPaymentService,
                              ReceiptService receiptService) {
        this.saleRepository = saleRepository;
        this.splitPaymentService = splitPaymentService;
        this.receiptService = receiptService;
    }

    @Transactional(readOnly = true)
    public void printCopy(Long saleId) {
        Sale sale = saleId == null ? null : saleRepository.findById(saleId).orElse(null);
        if (sale == null || sale.getStatus() != Sale.Status.FINALIZADA) {
            throw new IllegalArgumentException("Venda finalizada não encontrada.");
        }
        List<ReceiptPayment> payments = splitPaymentService.receiptPayments(sale);
        receiptService.printSplitSaleReceiptCopy(sale, payments);
    }
}
