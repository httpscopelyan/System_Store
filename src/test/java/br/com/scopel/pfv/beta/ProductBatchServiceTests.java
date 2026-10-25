package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.ProductBatch;
import br.com.scopel.pfv.beta.service.ProductBatchService;
import br.com.scopel.pfv.beta.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ProductBatchServiceTests {

    @Autowired
    private ProductBatchService batchService;
    @Autowired
    private ProductService productService;

    private Product newProduct() {
        Product p = new Product();
        p.setCode("LOT" + UUID.randomUUID().toString().substring(0, 8));
        p.setDescription("Produto com validade");
        p.setPrice(new BigDecimal("3.00"));
        p.setQuantity(new BigDecimal("0.000"));
        p.setUnit(Product.Unidade.UN);
        return productService.save(p);
    }

    @Test
    void registerBatchKeepsAvailable() {
        Product p = newProduct();

        ProductBatch batch = batchService.registerBatch(
                p.getCode(), "L001", LocalDate.now().plusDays(30), new BigDecimal("50"));

        assertNotNull(batch.getId());
        assertTrue(batch.getAvailable());
        assertEquals(0, batch.getQuantitySold().compareTo(BigDecimal.ZERO));
    }

    @Test
    void expiringUntilFindsSoonBatches() {
        Product p = newProduct();
        batchService.registerBatch(p.getCode(), "PERTO", LocalDate.now().plusDays(5), new BigDecimal("10"));
        batchService.registerBatch(p.getCode(), "LONGE", LocalDate.now().plusDays(365), new BigDecimal("10"));

        List<ProductBatch> soon = batchService.expiringUntil(LocalDate.now().plusDays(10));

        assertTrue(soon.stream().anyMatch(b -> "PERTO".equals(b.getBatchCode())));
        assertTrue(soon.stream().noneMatch(b -> "LONGE".equals(b.getBatchCode())));
    }
}
