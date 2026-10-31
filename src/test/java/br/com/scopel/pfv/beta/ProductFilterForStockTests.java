package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
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
class ProductFilterForStockTests {

    @Autowired
    private ProductService productService;

    private String token() {
        return "fs" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private Product create(String token, String suffix, String cost, String quantity, String minStock) {
        return productService.create(token + suffix, token + " " + suffix, Product.Unidade.UN,
                new BigDecimal("5.00"), new BigDecimal(cost), new BigDecimal(quantity),
                new BigDecimal(minStock), null, true);
    }

    private List<String> codes(List<Product> products) {
        return products.stream().map(Product::getCode).toList();
    }

    @Test
    void nameFilterWithoutFlagsListsEveryMatchSortedByName_RF74() {
        String token = token();
        create(token, "b", "1.00", "10", "0");
        create(token, "a", "1.00", "10", "0");
        assertEquals(List.of(token + "a", token + "b"), codes(productService.filterForStock("", token, false, false)));
    }

    @Test
    void codeFilterIsExactAndWinsOverName_RF74() {
        String token = token();
        create(token, "a", "1.00", "10", "0");
        create(token, "b", "1.00", "10", "0");
        assertEquals(List.of(token + "a"), codes(productService.filterForStock(token + "a", token, false, false)));
        assertTrue(productService.filterForStock("naoexiste" + token, "", false, false).isEmpty());
    }

    @Test
    void belowMinimumKeepsOnlyProductsAtOrBelowTheirMinimum_RF74() {
        String token = token();
        create(token, "baixo", "1.00", "3", "5");
        create(token, "igual", "1.00", "5", "5");
        create(token, "ok", "1.00", "9", "5");
        create(token, "semmin", "1.00", "0", "0");
        assertEquals(List.of(token + "baixo", token + "igual"),
                codes(productService.filterForStock("", token, true, false)));
    }

    @Test
    void withoutCostKeepsOnlyProductsWithNoCost_RF74() {
        String token = token();
        create(token, "custo", "2.00", "10", "0");
        create(token, "zero", "0.00", "10", "0");
        assertEquals(List.of(token + "zero"), codes(productService.filterForStock("", token, false, true)));
    }

    @Test
    void flagsCombineWithAnd_RF74() {
        String token = token();
        create(token, "a", "0.00", "1", "5");
        create(token, "b", "3.00", "1", "5");
        create(token, "c", "0.00", "9", "5");
        assertEquals(List.of(token + "a"), codes(productService.filterForStock("", token, true, true)));
    }

    @Test
    void inactiveProductsAreHiddenByDefaultAndListedByTheInactiveFilter_RF76() {
        String token = token();
        create(token, "ativo", "1.00", "10", "0");
        Product inactive = create(token, "inativo", "1.00", "10", "0");
        inactive.setActive(false);
        assertEquals(List.of(token + "ativo"), codes(productService.filterForStock("", token, false, false)));
        assertEquals(List.of(token + "inativo"), codes(productService.filterForStock("", token, false, false, true)));
    }

    @Test
    void inactivatingAProductMovesItToTheInactiveList_RF76() {
        String token = token();
        create(token, "x", "1.00", "10", "0");
        User manager = new User();
        manager.setRole(User.Role.GERENTE);
        productService.inactivate(token + "x", manager);
        assertTrue(productService.filterForStock("", token, false, false).isEmpty());
        assertEquals(List.of(token + "x"), codes(productService.filterForStock("", token, false, false, true)));
    }
}
