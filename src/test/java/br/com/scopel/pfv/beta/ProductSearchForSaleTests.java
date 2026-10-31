package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ProductSearchForSaleTests {

    @Autowired
    private ProductService productService;

    private String token() {
        return "kx" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private Product create(String code, String description, boolean active) {
        return productService.create(code, description, Product.Unidade.UN, new BigDecimal("5.00"),
                new BigDecimal("3.00"), new BigDecimal("10"), BigDecimal.ZERO, null, active);
    }

    private List<String> codes(List<Product> products) {
        return products.stream().map(Product::getCode).toList();
    }

    @Test
    void exactCodeComesBeforeOtherCodesWithTheSamePrefix_RF58() {
        String token = token();
        create(token + "2", "Segundo " + token, true);
        create(token, "Primeiro " + token, true);
        assertEquals(token, productService.searchForSale(token, 20).get(0).getCode());
    }

    @Test
    void digitsListCodesThatStartWithThem_RF59() {
        String base = String.valueOf(900000000L + (System.nanoTime() % 99999999L));
        create(base + "1", "Pão " + base, true);
        create(base + "2", "Pastel " + base, true);
        create("x" + base, "Outro " + base, true);
        List<String> found = codes(productService.searchForSale(base, 20));
        assertTrue(found.contains(base + "1"));
        assertTrue(found.contains(base + "2"));
        assertTrue(found.stream().noneMatch(code -> code.equals("x" + base)));
    }

    @Test
    void textListsNamePrefixBeforeNameContains_RF58() {
        String token = token();
        create("c1" + token, token + " suco", true);
        create("c2" + token, "doce " + token + " fruta", true);
        create("c3" + token, "sem relação " + token.substring(2), true);
        List<String> found = codes(productService.searchForSale(token, 20));
        assertEquals(List.of("c1" + token, "c2" + token), found);
    }

    @Test
    void searchIgnoresCase_RF58() {
        String token = token();
        create("c1" + token, token.toUpperCase() + " Refrigerante", true);
        assertEquals(1, productService.searchForSale(token.toLowerCase(), 20).size());
    }

    @Test
    void inactiveProductsAreNeverListed_RF58() {
        String token = token();
        create("c1" + token, token + " ativo", true);
        create("c2" + token, token + " inativo", false);
        assertEquals(List.of("c1" + token), codes(productService.searchForSale(token, 20)));
    }

    @Test
    void resultsRespectTheLimit_RF58() {
        String token = token();
        for (int index = 0; index < 5; index++) {
            create("c" + index + token, token + " item " + index, true);
        }
        assertEquals(3, productService.searchForSale(token, 3).size());
    }

    @Test
    void blankOrNullTermReturnsNothing_RF58() {
        assertTrue(productService.searchForSale("  ", 20).isEmpty());
        assertTrue(productService.searchForSale(null, 20).isEmpty());
        assertTrue(productService.searchForSale("abc", 0).isEmpty());
    }

    @Test
    void sameProductMatchedTwiceAppearsOnce_RF58() {
        String token = token();
        create(token, token + " duplicado", true);
        assertEquals(1, productService.searchForSale(token, 20).size());
    }
}
