package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.alert.StockAlert;
import br.com.scopel.pfv.beta.service.alert.StockAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class StockAlertServiceTests {

    @Autowired
    private StockAlertService stockAlertService;

    @Autowired
    private ProductService productService;

    @Autowired
    private AppSettingService appSettingService;

    private String createProduct(Product.Unidade unit, String quantity, String minStock, boolean active) {
        String code = "alt " + UUID.randomUUID().toString().substring(0, 8);
        productService.create(code, "Produto " + code, unit, new BigDecimal("5.00"), new BigDecimal("3.00"),
                new BigDecimal(quantity), new BigDecimal(minStock), null, active);
        return code;
    }

    private Optional<StockAlert> alertFor(String code) {
        return stockAlertService.lowStockItems().stream().filter(alert -> alert.code().equals(code)).findFirst();
    }

    @Test
    void itemExactlyAtMinimumPlusMarginIsListedAsAttention_RN22() {
        String code = createProduct(Product.Unidade.UN, "15", "10", true);
        StockAlert alert = alertFor(code).orElseThrow();
        assertEquals(StockAlert.Severity.ATENCAO, alert.severity());
        assertEquals(0, BigDecimal.ZERO.compareTo(alert.suggestedQuantity()));
    }

    @Test
    void itemJustAboveMinimumPlusMarginIsNotListed_RN22() {
        String code = createProduct(Product.Unidade.KG, "15.001", "10", true);
        assertTrue(alertFor(code).isEmpty());
    }

    @Test
    void itemAtOrBelowMinimumIsCritical_RN22() {
        String code = createProduct(Product.Unidade.UN, "10", "10", true);
        StockAlert alert = alertFor(code).orElseThrow();
        assertEquals(StockAlert.Severity.CRITICO, alert.severity());
        assertEquals(0, new BigDecimal("5").compareTo(alert.suggestedQuantity()));
    }

    @Test
    void itemWithoutDefinedMinimumIsNeverListed_RN22() {
        String code = createProduct(Product.Unidade.UN, "0", "0", true);
        assertTrue(alertFor(code).isEmpty());
    }

    @Test
    void inactiveItemIsNeverListed_RN22() {
        String code = createProduct(Product.Unidade.UN, "1", "10", false);
        assertTrue(alertFor(code).isEmpty());
    }

    @Test
    void weightProductsUseDecimalQuantities_RN22() {
        String code = createProduct(Product.Unidade.KG, "3.250", "2.500", true);
        StockAlert alert = alertFor(code).orElseThrow();
        assertEquals(StockAlert.Severity.ATENCAO, alert.severity());
        assertEquals(0, new BigDecimal("4.250").compareTo(alert.suggestedQuantity()));
    }

    @Test
    void changingTheMarginInSettingsChangesTheResultWithoutRestart_RN22() {
        String code = createProduct(Product.Unidade.UN, "15", "10", true);
        appSettingService.set(AppSettingService.STOCK_MIN_ALERT_MARGIN, "2");
        assertTrue(alertFor(code).isEmpty());
        appSettingService.set(AppSettingService.STOCK_MIN_ALERT_MARGIN, "5");
        assertTrue(alertFor(code).isPresent());
    }

    @Test
    void invalidConfiguredMarginFallsBackToFive_RN22() {
        appSettingService.set(AppSettingService.STOCK_MIN_ALERT_MARGIN, "abc");
        assertEquals(0, new BigDecimal("5").compareTo(stockAlertService.alertMargin()));
        appSettingService.set(AppSettingService.STOCK_MIN_ALERT_MARGIN, "-3");
        assertEquals(0, new BigDecimal("5").compareTo(stockAlertService.alertMargin()));
    }

    @Test
    void suggestedQuantityIsNeverNegative_RN22() {
        String code = createProduct(Product.Unidade.UN, "14", "10", true);
        assertTrue(alertFor(code).orElseThrow().suggestedQuantity().signum() >= 0);
    }

    @Test
    void criticalItemsComeBeforeAttentionItems_RN22() {
        String attention = createProduct(Product.Unidade.UN, "15", "10", true);
        String critical = createProduct(Product.Unidade.UN, "1", "10", true);
        var codes = stockAlertService.lowStockItems().stream().map(StockAlert::code).toList();
        assertTrue(codes.indexOf(critical) < codes.indexOf(attention));
    }
}
