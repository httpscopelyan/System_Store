package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.PriceTable;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.PriceTableService;
import br.com.scopel.pfv.beta.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PriceTableServiceTests {

    @Autowired private PriceTableService priceTableService;
    @Autowired private ProductService productService;

    private Product product(BigDecimal price, String category) {
        Product p = new Product();
        p.setCode(UUID.randomUUID().toString().substring(0, 13));
        p.setDescription("Produto");
        p.setPrice(price);
        p.setQuantity(new BigDecimal("10.000"));
        p.setUnit(Product.Unidade.UN);
        p.setCategory(category);
        return productService.save(p);
    }

    @Test
    void tablePriceOverridesBasePrice() {
        PriceTable atacado = priceTableService.createTable("Atacado " + UUID.randomUUID());
        Product p = product(new BigDecimal("10.00"), null);

        priceTableService.setPrice(atacado, p, new BigDecimal("8.50"));

        assertEquals(0, new BigDecimal("8.50").compareTo(priceTableService.getPrice(atacado, p)));
    }

    @Test
    void getPriceFallsBackToBaseWhenNoItem() {
        PriceTable varejo = priceTableService.createTable("Varejo " + UUID.randomUUID());
        Product p = product(new BigDecimal("12.00"), null);

        assertEquals(0, new BigDecimal("12.00").compareTo(priceTableService.getPrice(varejo, p)));
    }

    @Test
    void bulkAdjustByCategoryRaisesPrices() {
        String cat = "CAT" + UUID.randomUUID().toString().substring(0, 6);
        Product a = product(new BigDecimal("10.00"), cat);
        Product b = product(new BigDecimal("20.00"), cat);

        int changed = productService.adjustPricesByPercent(new BigDecimal("10"), cat);

        assertEquals(2, changed);
        assertEquals(0, new BigDecimal("11.00").compareTo(productService.findCode(a.getCode()).getPrice()));
        assertEquals(0, new BigDecimal("22.00").compareTo(productService.findCode(b.getCode()).getPrice()));
    }
}
