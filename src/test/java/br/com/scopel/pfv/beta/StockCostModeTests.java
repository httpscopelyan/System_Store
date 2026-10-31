package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.CostAveraging;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.StockCostMode;
import br.com.scopel.pfv.beta.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class StockCostModeTests {

    @Autowired private StockService stockService;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private AppSettingService appSettingService;

    private String productWith(String stock, String cost) {
        String code = "cost " + UUID.randomUUID().toString().substring(0, 8);
        productService.create(code, "Produto " + code, Product.Unidade.UN, new BigDecimal("9.00"),
                new BigDecimal(cost), new BigDecimal(stock), BigDecimal.ZERO, null, true);
        return code;
    }

    private void useAverageCost() {
        appSettingService.set(AppSettingService.STOCK_COST_MODE, StockCostMode.MEDIA_PONDERADA.name());
    }

    private BigDecimal costOf(String code) {
        return productRepository.findByCodeIgnoreCase(code).orElseThrow().getCostPrice();
    }

    @Test
    void defaultModeOverwritesTheCostWithTheLastEntryCost_RF71() {
        String code = productWith("10", "4.00");
        stockService.registerEntry(code, new BigDecimal("10"), new BigDecimal("5.00"), "nota 1", null);
        assertEquals(new BigDecimal("5.00"), costOf(code));
        assertEquals(StockCostMode.ULTIMO, stockService.costMode());
    }

    @Test
    void averageModeWeightsTheCostByStock_RF71() {
        useAverageCost();
        String code = productWith("10", "4.00");
        stockService.registerEntry(code, new BigDecimal("10"), new BigDecimal("5.00"), "nota 1", null);
        assertEquals(new BigDecimal("4.50"), costOf(code));
    }

    @Test
    void averageModeRoundsHalfUp_RF71() {
        useAverageCost();
        String code = productWith("3", "1.00");
        stockService.registerEntry(code, new BigDecimal("1"), new BigDecimal("2.00"), "nota 1", null);
        assertEquals(new BigDecimal("1.25"), costOf(code));
    }

    @Test
    void averageModeWithNoStockUsesTheEntryCost_RF71() {
        useAverageCost();
        String code = productWith("0", "4.00");
        stockService.registerEntry(code, new BigDecimal("10"), new BigDecimal("5.00"), "nota 1", null);
        assertEquals(new BigDecimal("5.00"), costOf(code));
    }

    @Test
    void averageModeWithNegativeStockUsesTheEntryCost_RF71() {
        useAverageCost();
        String code = productWith("5", "4.00");
        Product product = productRepository.findByCodeIgnoreCase(code).orElseThrow();
        product.setQuantity(new BigDecimal("-2.000"));
        productRepository.save(product);
        stockService.registerEntry(code, new BigDecimal("10"), new BigDecimal("5.00"), "nota 1", null);
        assertEquals(new BigDecimal("5.00"), costOf(code));
    }

    @Test
    void averageModeWithoutAPreviousCostUsesTheEntryCost_RF71() {
        useAverageCost();
        String code = productWith("10", "0.00");
        stockService.registerEntry(code, new BigDecimal("10"), new BigDecimal("5.00"), "nota 1", null);
        assertEquals(new BigDecimal("5.00"), costOf(code));
    }

    @Test
    void entryWithoutCostKeepsTheCostInBothModes_RF71() {
        String code = productWith("10", "4.00");
        stockService.registerEntry(code, new BigDecimal("5"), null, "nota 1", null);
        assertEquals(new BigDecimal("4.00"), costOf(code));
        useAverageCost();
        stockService.registerEntry(code, new BigDecimal("5"), null, "nota 2", null);
        assertEquals(new BigDecimal("4.00"), costOf(code));
    }

    @Test
    void unknownConfiguredModeFallsBackToLastCost_RF71() {
        appSettingService.set(AppSettingService.STOCK_COST_MODE, "qualquer coisa");
        assertEquals(StockCostMode.ULTIMO, stockService.costMode());
    }

    @Test
    void previewShowsTheSameCostTheEntryWillRecord_RF71() {
        useAverageCost();
        String code = productWith("10", "4.00");
        StockService.CostPreview preview = stockService.previewEntryCost(code, new BigDecimal("10"), new BigDecimal("5.00"));
        assertEquals(new BigDecimal("4.00"), preview.currentCost());
        assertEquals(new BigDecimal("4.50"), preview.newCost());
        assertEquals(StockCostMode.MEDIA_PONDERADA, preview.mode());
        stockService.registerEntry(code, new BigDecimal("10"), new BigDecimal("5.00"), "nota 1", null);
        assertEquals(preview.newCost(), costOf(code));
    }

    @Test
    void switchingTheModeDoesNotRewriteExistingCosts_RF71() {
        String code = productWith("10", "4.00");
        useAverageCost();
        assertEquals(new BigDecimal("4.00"), costOf(code));
    }

    @Test
    void weightedAverageHelperHandlesFractionalWeights_RF71() {
        assertEquals(new BigDecimal("40.00"), CostAveraging.weightedAverageCost(new BigDecimal("1.500"),
                new BigDecimal("40.00"), new BigDecimal("2.500"), new BigDecimal("40.00")));
        assertEquals(new BigDecimal("1.01"), CostAveraging.weightedAverageCost(new BigDecimal("1"),
                new BigDecimal("1.00"), new BigDecimal("2"), new BigDecimal("1.01")));
    }
}
