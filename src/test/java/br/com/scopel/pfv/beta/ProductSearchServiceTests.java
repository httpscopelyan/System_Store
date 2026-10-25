package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bordas de leitura de {@link ProductService} que sustentam a Consulta de Preço
 * (#G3, RF04/RF05) — caminho SÓ LEITURA: nenhuma gravação ou baixa de estoque.
 */
@SpringBootTest
@Transactional
class ProductSearchServiceTests {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    private Product newProduct(String description, BigDecimal price) {
        Product product = new Product();
        product.setCode(UUID.randomUUID().toString().substring(0, 13));
        product.setDescription(description);
        product.setPrice(price);
        product.setQuantity(new BigDecimal("10.000"));
        product.setUnit(Product.Unidade.UN);
        product.setActive(true);
        return productRepository.save(product);
    }

    @Test
    void searchByNameBlankReturnsAllInsteadOfThrowing() {
        // nome vazio/branco não é erro: cai no findAll (RF05)
        assertDoesNotThrow(() -> productService.searchByName(""));
        assertDoesNotThrow(() -> productService.searchByName("   "));
        assertDoesNotThrow(() -> productService.searchByName(null));
    }

    @Test
    void searchByNameNoMatchReturnsEmptyList() {
        List<Product> matches = productService.searchByName("ZZZ_INEXISTENTE_" + UUID.randomUUID());
        assertNotNull(matches);
        assertTrue(matches.isEmpty());
    }

    @Test
    void searchByNamePartialMatchesIgnoringCase() {
        Product saved = newProduct("Refrigerante Cola 2L " + UUID.randomUUID(), new BigDecimal("8.50"));
        List<Product> matches = productService.searchByName("refrigerante cola 2l");
        assertTrue(matches.stream().anyMatch(p -> p.getId().equals(saved.getId())));
    }

    @Test
    void findCodeUnknownThrowsProductNotfind() {
        // código inexistente é erro explícito (usado pela Consulta de Preço)
        assertThrows(ProductNotfindException.class,
                () -> productService.findCode("NO_SUCH_CODE_" + UUID.randomUUID()));
    }

    @Test
    void findCodePreservesLeadingZeros() {
        // código de barras = String; zero à esquerda não pode virar número
        String code = "0" + UUID.randomUUID().toString().substring(0, 12);
        Product saved = newProduct("Produto com zero a esquerda", new BigDecimal("1.00"));
        saved.setCode(code);
        productRepository.save(saved);

        Product found = productService.findCode(code);
        assertEquals(code, found.getCode());
    }
}
