package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.PricingSuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Transactional
class PricingSuggestionServiceTests {

    @Autowired
    private PricingSuggestionService pricingSuggestionService;

    @Autowired
    private AppSettingService appSettingService;

    private Product productWith(String cost, String price) {
        Product product = new Product();
        product.setCostPrice(cost == null ? null : new BigDecimal(cost));
        product.setPrice(price == null ? null : new BigDecimal(price));
        return product;
    }

    @Test
    void suggestionAppliesMarginOverCost_RF65() {
        BigDecimal suggestion = pricingSuggestionService.suggestFor(new BigDecimal("0.80"), new BigDecimal("80"));
        assertEquals(new BigDecimal("1.44"), suggestion);
    }

    @Test
    void suggestionRoundsHalfUp_RF65() {
        BigDecimal suggestion = pricingSuggestionService.suggestFor(new BigDecimal("0.35"), new BigDecimal("10"));
        assertEquals(new BigDecimal("0.39"), suggestion);
    }

    @Test
    void zeroMarginSuggestsTheCost_RF65() {
        BigDecimal suggestion = pricingSuggestionService.suggestFor(new BigDecimal("5.00"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("5.00"), suggestion);
    }

    @Test
    void missingOrNonPositiveCostHasNoSuggestion_RF65() {
        assertNull(pricingSuggestionService.suggestFor(null, new BigDecimal("30")));
        assertNull(pricingSuggestionService.suggestFor(BigDecimal.ZERO, new BigDecimal("30")));
        assertNull(pricingSuggestionService.suggestFor(new BigDecimal("-1"), new BigDecimal("30")));
    }

    @Test
    void missingOrNegativeMarginHasNoSuggestion_RF65() {
        assertNull(pricingSuggestionService.suggestFor(new BigDecimal("5.00"), null));
        assertNull(pricingSuggestionService.suggestFor(new BigDecimal("5.00"), new BigDecimal("-10")));
    }

    @Test
    void defaultMarginFallsBackToThirtyWhenNotConfigured_RF65() {
        BigDecimal suggestion = pricingSuggestionService.suggestFor(new BigDecimal("10.00"));
        assertEquals(new BigDecimal("13.00"), suggestion);
    }

    @Test
    void defaultMarginComesFromSettings_RF65() {
        appSettingService.set(AppSettingService.PRICING_DEFAULT_MARGIN_PERCENT, "50");
        assertEquals(new BigDecimal("15.00"), pricingSuggestionService.suggestFor(new BigDecimal("10.00")));
    }

    @Test
    void invalidConfiguredMarginFallsBackToThirty_RF65() {
        appSettingService.set(AppSettingService.PRICING_DEFAULT_MARGIN_PERCENT, "abc");
        assertEquals(0, new BigDecimal("30").compareTo(pricingSuggestionService.defaultMarginPercent()));
        appSettingService.set(AppSettingService.PRICING_DEFAULT_MARGIN_PERCENT, "-5");
        assertEquals(0, new BigDecimal("30").compareTo(pricingSuggestionService.defaultMarginPercent()));
    }

    @Test
    void profitAndMarginAreDerivedFromPriceAndCost_RF64() {
        Product product = productWith("0.80", "1.44");
        assertEquals(new BigDecimal("0.64"), pricingSuggestionService.profitOf(product));
        assertEquals(new BigDecimal("80.0"), pricingSuggestionService.marginPercentOf(product));
    }

    @Test
    void productWithoutCostHasNoProfitOrMargin_RF64() {
        Product product = productWith("0.00", "1.44");
        assertNull(pricingSuggestionService.profitOf(product));
        assertNull(pricingSuggestionService.marginPercentOf(product));
        assertNull(pricingSuggestionService.suggestFor(product));
    }
}
