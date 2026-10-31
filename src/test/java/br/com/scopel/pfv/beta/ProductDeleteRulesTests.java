package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ProductDeleteRulesTests {

    @Autowired private ProductService productService;

    private String newProduct() {
        String code = "del " + UUID.randomUUID().toString().substring(0, 8);
        productService.create(code, "Produto " + code, Product.Unidade.UN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.ZERO, null, true);
        return code;
    }

    private User user(User.Role role) {
        User user = new User();
        user.setRole(role);
        return user;
    }

    @Test
    void managerDeletesByInactivatingAndKeepsTheRecord_RN31() {
        String code = newProduct();
        Product deleted = productService.inactivate(code, user(User.Role.GERENTE));
        assertFalse(deleted.getActive());
        assertEquals(code, productService.findCode(code).getCode());
    }

    @Test
    void operatorOrNobodyCannotDelete_RN31() {
        String code = newProduct();
        assertThrows(ManagerAuthorizationRequiredException.class, () -> productService.inactivate(code, user(User.Role.OPERADOR)));
        assertThrows(ManagerAuthorizationRequiredException.class, () -> productService.inactivate(code, null));
        assertTrue(productService.findCode(code).getActive());
    }
}
