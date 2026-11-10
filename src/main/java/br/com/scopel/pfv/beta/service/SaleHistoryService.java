package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SaleHistoryService {

    public static final int MAX_PAGE_SIZE = 200;

    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;

    public SaleHistoryService(SaleRepository saleRepository, PaymentRepository paymentRepository) {
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public Page<SaleHistoryEntry> finalizedSales(Long operatorId, LocalDateTime start, LocalDateTime end,
                                                 int pageNumber, int pageSize) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("Informe um período válido.");
        }
        if (pageNumber < 0) {
            throw new IllegalArgumentException("A página não pode ser negativa.");
        }
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("O tamanho da página deve ficar entre 1 e " + MAX_PAGE_SIZE + ".");
        }
        return saleRepository
                .findHistory(Sale.Status.FINALIZADA, operatorId, start, end, PageRequest.of(pageNumber, pageSize))
                .map(this::toEntry);
    }

    private SaleHistoryEntry toEntry(Sale sale) {
        List<SaleHistoryEntry.Item> items = sale.getItems().stream().map(this::toItem).toList();
        List<SaleHistoryEntry.PaymentPart> payments = paymentRepository
                .findBySale_IdOrderByCreatedAtAscIdAsc(sale.getId()).stream().map(this::toPaymentPart).toList();
        return new SaleHistoryEntry(sale.getId(), sale.getCreatedAt(),
                sale.getOperator() == null ? null : sale.getOperator().getId(),
                sale.getOperator() == null ? null : sale.getOperator().getName(),
                sale.getCustomer() == null ? null : sale.getCustomer().getName(),
                sale.getDiscount(), sale.getSurcharge(), sale.getTotal(), items, payments);
    }

    private SaleHistoryEntry.Item toItem(SaleItem item) {
        return new SaleHistoryEntry.Item(item.getProduct().getCode(), item.getProduct().getDescription(),
                item.getQuantity(), item.getUnitPrice(), item.getDiscount(), item.getTotalPrice());
    }

    private SaleHistoryEntry.PaymentPart toPaymentPart(Payment payment) {
        return new SaleHistoryEntry.PaymentPart(payment.getMethod(), payment.getCardType(),
                payment.getAmountReceived(), payment.getChange(),
                payment.getAmountReceived().subtract(payment.getChange()));
    }
}
