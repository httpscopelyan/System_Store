package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class ProductCodeChangeTests {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Product product() {
        Product p = new Product();
        p.setCode("c" + unique());
        p.setDescription("Produto " + unique());
        p.setPrice(new BigDecimal("5.00"));
        p.setQuantity(new BigDecimal("3.000"));
        p.setUnit(Product.Unidade.UN);
        p.setActive(true);
        return productRepository.save(p);
    }

    @Test
    void changesTheCodeKeepingTheSameProduct() {
        Product p = product();
        String newCode = "n" + unique();

        productService.changeCode(p.getId(), newCode);

        assertEquals(p.getId(), productService.findCode(newCode).getId());
        assertEquals(0, new BigDecimal("3").compareTo(productService.findCode(newCode).getQuantity()));
    }

    @Test
    void refusesACodeUsedByAnotherProduct() {
        Product first = product();
        Product second = product();

        assertThrows(RegisterProductDuplicateException.class,
                () -> productService.changeCode(second.getId(), first.getCode()));
    }

    @Test
    void refusesBlankOrInvalidCodesAndAllowsKeepingTheSame() {
        Product p = product();

        assertThrows(IllegalArgumentException.class, () -> productService.changeCode(p.getId(), "  "));
        assertThrows(IllegalArgumentException.class, () -> productService.changeCode(p.getId(), "a@b"));
        assertEquals(p.getCode(), productService.changeCode(p.getId(), p.getCode()).getCode());
    }

    @Test
    void fullUpdateWithADuplicatedCodeChangesNothing() {
        Product first = product();
        Product second = product();
        String originalDescription = second.getDescription();

        assertThrows(RegisterProductDuplicateException.class, () -> productService.update(second.getId(),
                first.getCode(), "Outro nome", Product.Unidade.UN, new BigDecimal("9.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, null, true));

        assertEquals(originalDescription, productRepository.findById(second.getId()).orElseThrow().getDescription());
    }
}
