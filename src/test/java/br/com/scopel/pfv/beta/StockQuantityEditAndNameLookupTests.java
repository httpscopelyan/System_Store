package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.exception.UserDuplicatedException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.StockService;
import br.com.scopel.pfv.beta.service.UserService;
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
class StockQuantityEditAndNameLookupTests {

    @Autowired private StockService stockService;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserService userService;

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Product product(String description) {
        Product p = new Product();
        p.setCode("c" + unique());
        p.setDescription(description);
        p.setPrice(new BigDecimal("5.00"));
        p.setQuantity(new BigDecimal("10.000"));
        p.setUnit(Product.Unidade.UN);
        p.setActive(true);
        return productRepository.save(p);
    }

    private User user(String login, User.Role role) {
        User u = new User();
        u.setLogin(login);
        u.setName(login);
        u.setPassword("senha1");
        u.setRole(role);
        return userService.save(u);
    }

    private User dayana() {
        try {
            return userService.findLogin("Dayana");
        } catch (UserNotFindException notSeededYet) {
            return user("Dayana", User.Role.OPERADOR);
        }
    }

    @Test
    void dayanaChangesQuantityDirectlyAndTheMovementIsRecorded() {
        Product p = product("Arroz " + unique());
        User dayana = dayana();

        Product updated = stockService.adjustQuantityInline(p.getCode(), new BigDecimal("42"), dayana);

        assertEquals(0, new BigDecimal("42").compareTo(updated.getQuantity()));
        assertEquals(1, stockService.history(p.getCode()).size());
    }

    @Test
    void ordinaryOperatorIsRefusedAndQuantityStays() {
        Product p = product("Feijão " + unique());
        User maria = new User();
        maria.setLogin("Maria");
        maria.setName("Maria");
        maria.setRole(User.Role.OPERADOR);

        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> stockService.adjustQuantityInline(p.getCode(), new BigDecimal("1"), maria));

        assertEquals(0, new BigDecimal("10").compareTo(productRepository.findById(p.getId()).orElseThrow().getQuantity()));
    }

    @Test
    void negativeQuantityIsRefused() {
        Product p = product("Sal " + unique());
        User dayana = dayana();

        assertThrows(IllegalArgumentException.class,
                () -> stockService.adjustQuantityInline(p.getCode(), new BigDecimal("-1"), dayana));
    }

    @Test
    void nameTypedInsteadOfCodeResolvesToTheCodeWhenUnique() {
        String marker = "Zzxq" + unique();
        Product p = product(marker + " Premium");

        assertEquals(p.getCode(), productService.resolveCode(marker));
        assertEquals(p.getCode(), productService.resolveCode(p.getCode()));
    }

    @Test
    void ambiguousNameAsksForTheCodeAndUnknownNameIsNotFound() {
        String marker = "Zzxq" + unique();
        product(marker + " A");
        product(marker + " B");

        assertThrows(IllegalArgumentException.class, () -> productService.resolveCode(marker));
        assertThrows(ProductNotfindException.class, () -> productService.resolveCode("nada-" + unique()));
    }

    @Test
    void renamingIntoAPrivilegedNameIsRefused() {
        User operator = user("op" + unique(), User.Role.OPERADOR);

        assertThrows(UserDuplicatedException.class,
                () -> userService.updateOwnCredentials(operator.getId(), "senha1", "Dayana", ""));
        assertThrows(UserDuplicatedException.class,
                () -> userService.updateOwnCredentials(operator.getId(), "senha1", "Ana Clara", ""));
    }

    @Test
    void anaClaraCannotRenameHerself() {
        User ana = user("Ana Clara", User.Role.OPERADOR);

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateOwnCredentials(ana.getId(), "senha1", "Outra" + unique(), ""));
    }
}
