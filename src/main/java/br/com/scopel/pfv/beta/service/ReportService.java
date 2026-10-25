package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.SaleItemRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Relatórios (PDV Prático: Relatorios, ResumoMovimento). Consultas somente
 * leitura sobre vendas FINALIZADAS no período. Lucro é aproximado (custo atual).
 */
@Service
public class ReportService {

    public record SalesSummary(BigDecimal totalSales, BigDecimal grossProfit,
                               BigDecimal totalDiscounts, long canceledCount) {
    }

    public record NamedAmount(String name, BigDecimal amount) {
    }

    public record ProductQuantity(String description, BigDecimal quantity) {
    }

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentRepository paymentRepository;

    public ReportService(SaleRepository saleRepository,
                         SaleItemRepository saleItemRepository,
                         PaymentRepository paymentRepository) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.paymentRepository = paymentRepository;
    }

    /** Resumo do movimento: vendas, lucro, descontos e cancelamentos no período. */
    public SalesSummary summary(LocalDateTime start, LocalDateTime end) {
        BigDecimal sales = nz(saleRepository.sumTotalByStatusAndPeriod(Sale.Status.FINALIZADA, start, end));
        BigDecimal profit = nz(saleItemRepository.grossProfit(Sale.Status.FINALIZADA, start, end));
        BigDecimal discounts = nz(saleRepository.sumDiscountByStatusAndPeriod(Sale.Status.FINALIZADA, start, end));
        long canceled = saleRepository.countByStatusAndCanceledPeriod(Sale.Status.CANCELADA, start, end);
        return new SalesSummary(sales, profit, discounts, canceled);
    }

    /** Vendas por forma de pagamento (RF45). */
    public List<NamedAmount> byPaymentMethod(LocalDateTime start, LocalDateTime end) {
        List<NamedAmount> result = new ArrayList<>();
        for (Object[] row : paymentRepository.totalByMethod(Sale.Status.FINALIZADA, start, end)) {
            PaymentMethod method = (PaymentMethod) row[0];
            result.add(new NamedAmount(method.name(), nz((BigDecimal) row[1])));
        }
        return result;
    }

    /** Vendas por operador (RF46). */
    public List<NamedAmount> byOperator(LocalDateTime start, LocalDateTime end) {
        List<NamedAmount> result = new ArrayList<>();
        for (Object[] row : saleRepository.totalByOperator(Sale.Status.FINALIZADA, start, end)) {
            result.add(new NamedAmount((String) row[0], nz((BigDecimal) row[1])));
        }
        return result;
    }

    /** Produtos mais vendidos no período (RF47), limitado a {@code limit}. */
    public List<ProductQuantity> topProducts(LocalDateTime start, LocalDateTime end, int limit) {
        List<ProductQuantity> result = new ArrayList<>();
        for (Object[] row : saleItemRepository.topProducts(Sale.Status.FINALIZADA, start, end)) {
            if (result.size() >= limit) {
                break;
            }
            result.add(new ProductQuantity((String) row[0], nz((BigDecimal) row[1])));
        }
        return result;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
