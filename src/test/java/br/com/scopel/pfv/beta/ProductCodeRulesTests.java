package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ProductCodeRulesTests {

    @Autowired
    private ProductService productService;

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Product create(String code, String minStock, String category) {
        return productService.create(code, "Produto de teste", Product.Unidade.UN,
                new BigDecimal("1.44"), new BigDecimal("0.80"), new BigDecimal("12"),
                new BigDecimal(minStock), category, true);
    }

    @Test
    void textCodeIsAccepted_RF70() {
        String code = "pao frances " + uniqueSuffix();
        Product created = create(code, "10", "Padaria");
        assertEquals(code, created.getCode());
        assertEquals(0, new BigDecimal("10").compareTo(created.getMinStock()));
        assertEquals("Padaria", created.getCategory());
    }

    @Test
    void numericCodeKeepsLeadingZeros_RF70() {
        String code = "000" + System.nanoTime() % 100000;
        Product created = create(code, "0", null);
        assertTrue(created.getCode().startsWith("000"));
        assertEquals(created.getCode(), productService.findCode(code).getCode());
    }

    @Test
    void duplicateCodeIgnoringCaseIsRefused_RF70() {
        String suffix = uniqueSuffix();
        create("Pao " + suffix, "0", null);
        assertThrows(RegisterProductDuplicateException.class, () -> create("PAO " + suffix, "0", null));
    }

    @Test
    void findCodeIgnoresCaseAndSurroundingSpaces_RF70() {
        String suffix = uniqueSuffix();
        Product created = create("Suco " + suffix, "0", null);
        assertEquals(created.getId(), productService.findCode("  SUCO " + suffix + " ").getId());
    }

    @Test
    void unknownCodeIsNotFound_RF70() {
        assertThrows(ProductNotfindException.class, () -> productService.findCode("inexistente-" + uniqueSuffix()));
    }

    @Test
    void invalidCharactersAreRefused_RF70() {
        assertThrows(IllegalArgumentException.class, () -> create("pao/frances", "0", null));
        assertThrows(IllegalArgumentException.class, () -> create("pao*frances", "0", null));
    }

    @Test
    void blankOrTooLongCodeIsRefused_RF70() {
        assertThrows(IllegalArgumentException.class, () -> create("   ", "0", null));
        assertThrows(IllegalArgumentException.class, () -> create(null, "0", null));
        assertThrows(IllegalArgumentException.class, () -> create("x".repeat(31), "0", null));
    }

    @Test
    void negativeMinimumStockIsRefused_RF66() {
        assertThrows(IllegalArgumentException.class, () -> create("neg " + uniqueSuffix(), "-1", null));
    }

    @Test
    void blankCategoryIsStoredAsNull_RF66() {
        Product created = create("cat " + uniqueSuffix(), "0", "   ");
        assertEquals(null, created.getCategory());
    }

    @Test
    void descriptionAndUnitAreRequired_RF66() {
        assertThrows(IllegalArgumentException.class, () -> productService.create(
                "d " + uniqueSuffix(), " ", Product.Unidade.UN, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null, true));
        assertThrows(IllegalArgumentException.class, () -> productService.create(
                "u " + uniqueSuffix(), "x", null, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null, true));
    }
}
