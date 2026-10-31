package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingSuggestionService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal FALLBACK_MARGIN_PERCENT = new BigDecimal("30");

    private final AppSettingService appSettingService;

    public PricingSuggestionService(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
    }

    public BigDecimal defaultMarginPercent() {
        BigDecimal configured = appSettingService.getBigDecimal(
                AppSettingService.PRICING_DEFAULT_MARGIN_PERCENT, FALLBACK_MARGIN_PERCENT);
        return configured.signum() < 0 ? FALLBACK_MARGIN_PERCENT : configured;
    }

    public BigDecimal suggestFor(BigDecimal cost, BigDecimal marginPercent) {
        if (cost == null || cost.signum() <= 0 || marginPercent == null || marginPercent.signum() < 0) {
            return null;
        }
        BigDecimal factor = BigDecimal.ONE.add(marginPercent.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        return cost.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal suggestFor(BigDecimal cost) {
        return suggestFor(cost, defaultMarginPercent());
    }

    public BigDecimal suggestFor(Product product) {
        return suggestFor(product.getCostPrice());
    }

    public BigDecimal profitOf(BigDecimal price, BigDecimal cost) {
        if (price == null || cost == null || cost.signum() <= 0) {
            return null;
        }
        return price.subtract(cost).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal profitOf(Product product) {
        return profitOf(product.getPrice(), product.getCostPrice());
    }

    public BigDecimal marginPercentOf(BigDecimal price, BigDecimal cost) {
        if (price == null || cost == null || cost.signum() <= 0) {
            return null;
        }
        return price.subtract(cost).multiply(HUNDRED).divide(cost, 1, RoundingMode.HALF_UP);
    }

    public BigDecimal marginPercentOf(Product product) {
        return marginPercentOf(product.getPrice(), product.getCostPrice());
    }
}
