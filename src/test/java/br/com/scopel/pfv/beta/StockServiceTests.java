package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.StockMovement;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class StockServiceTests {

    @Autowired
    private StockService stockService;
    @Autowired
    private ProductService productService;

    private Product newProduct(BigDecimal initialQty) {
        Product p = new Product();
        p.setCode("STK" + UUID.randomUUID().toString().substring(0, 8));
        p.setDescription("Produto teste estoque");
        p.setPrice(new BigDecimal("10.00"));
        p.setQuantity(initialQty);
        p.setUnit(Product.Unidade.UN);
        return productService.save(p);
    }

    @Test
    void entryIncreasesStockAndUpdatesCost() {
        Product p = newProduct(new BigDecimal("5.000"));

        Product after = stockService.registerEntry(
                p.getCode(), new BigDecimal("10"), new BigDecimal("4.50"), "compra", null);

        assertEquals(0, after.getQuantity().compareTo(new BigDecimal("15.000")));
        assertEquals(0, after.getCostPrice().compareTo(new BigDecimal("4.50")));
    }

    @Test
    void adjustSetsAbsoluteQuantity() {
        Product p = newProduct(new BigDecimal("20.000"));

        Product after = stockService.adjust(p.getCode(), new BigDecimal("12"), "contagem", null);

        assertEquals(0, after.getQuantity().compareTo(new BigDecimal("12.000")));
    }

    @Test
    void historyRecordsMovements() {
        Product p = newProduct(new BigDecimal("0.000"));
        stockService.registerEntry(p.getCode(), new BigDecimal("3"), null, "e1", null);
        stockService.adjust(p.getCode(), new BigDecimal("2"), "a1", null);

        List<StockMovement> history = stockService.history(p.getCode());

        assertEquals(2, history.size());
        // mais recente primeiro => o ajuste
        assertEquals(StockMovement.Type.AJUSTE, history.get(0).getType());
    }
}
