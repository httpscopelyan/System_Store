package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.ProductBulkEditService;
import br.com.scopel.pfv.beta.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class StockPermissionTests {

    @Autowired private ProductBulkEditService bulkEditService;
    @Autowired private StockService stockService;
    @Autowired private ProductRepository productRepository;

    private Product product() {
        Product p = new Product();
        p.setCode("c" + UUID.randomUUID().toString().substring(0, 8));
        p.setDescription("Produto");
        p.setPrice(new BigDecimal("5.00"));
        p.setQuantity(new BigDecimal("10.000"));
        p.setUnit(Product.Unidade.UN);
        p.setActive(true);
        return productRepository.save(p);
    }

    private User named(String name, User.Role role) {
        User user = new User();
        user.setLogin(name);
        user.setName(name);
        user.setRole(role);
        return user;
    }

    @Test
    void dayanaChangesTheSalePriceWithoutAManager() {
        Product p = product();

        bulkEditService.applyPrice(List.of(p.getId()), new BigDecimal("7.50"), named("Dayana", User.Role.OPERADOR));

        assertEquals(0, new BigDecimal("7.50").compareTo(productRepository.findById(p.getId()).orElseThrow().getPrice()));
    }

    @Test
    void otherOperatorsStillNeedAManagerToChangeThePrice() {
        Product p = product();

        assertThrows(ManagerAuthorizationRequiredException.class, () -> bulkEditService.applyPrice(
                List.of(p.getId()), new BigDecimal("7.50"), named("Maria", User.Role.OPERADOR)));
    }

    @Test
    void anaClaraCannotRegisterEntriesOrAdjustments() {
        Product p = product();
        User ana = named("Ana Clara", User.Role.OPERADOR);

        assertThrows(IllegalArgumentException.class,
                () -> stockService.registerEntry(p.getCode(), BigDecimal.ONE, null, "nota", ana));
        assertThrows(IllegalArgumentException.class,
                () -> stockService.adjust(p.getCode(), BigDecimal.ONE, "contagem", ana));
        assertEquals(0, new BigDecimal("10").compareTo(productRepository.findById(p.getId()).orElseThrow().getQuantity()));
    }
}
