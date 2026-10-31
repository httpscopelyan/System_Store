package br.com.scopel.pfv.beta.service.alert;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.AppSettingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
public class StockAlertService {

    private static final BigDecimal DEFAULT_MARGIN = new BigDecimal("5");

    private final ProductRepository productRepository;
    private final AppSettingService appSettingService;

    public StockAlertService(ProductRepository productRepository, AppSettingService appSettingService) {
        this.productRepository = productRepository;
        this.appSettingService = appSettingService;
    }

    public BigDecimal alertMargin() {
        BigDecimal configured = appSettingService.getBigDecimal(
                AppSettingService.STOCK_MIN_ALERT_MARGIN, DEFAULT_MARGIN);
        return configured.signum() < 0 ? DEFAULT_MARGIN : configured;
    }

    public List<StockAlert> lowStockItems() {
        BigDecimal margin = alertMargin();
        return productRepository.findLowStock(margin).stream()
                .map(product -> toAlert(product, margin))
                .sorted(Comparator.comparing(StockAlert::severity)
                        .thenComparing(StockAlert::description, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public int countLowStockItems() {
        return lowStockItems().size();
    }

    private StockAlert toAlert(Product product, BigDecimal margin) {
        BigDecimal targetStock = product.getMinStock().add(margin);
        BigDecimal suggested = targetStock.subtract(product.getQuantity()).max(BigDecimal.ZERO)
                .setScale(3, RoundingMode.HALF_UP);
        StockAlert.Severity severity = product.getQuantity().compareTo(product.getMinStock()) <= 0
                ? StockAlert.Severity.CRITICO
                : StockAlert.Severity.ATENCAO;
        return new StockAlert(product.getCode(), product.getDescription(), product.getUnit(),
                product.getQuantity(), product.getMinStock(), suggested, severity);
    }
}
