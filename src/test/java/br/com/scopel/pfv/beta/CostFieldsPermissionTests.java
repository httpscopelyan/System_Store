package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.ProductBulkEditService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.PurchaseService;
import br.com.scopel.pfv.beta.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class CostFieldsPermissionTests {

    @Autowired private ProductService productService;
    @Autowired private ProductBulkEditService bulkEditService;
    @Autowired private StockService stockService;
    @Autowired private PurchaseService purchaseService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    private Product product(Product.Unidade unit) {
        return productService.create("k " + UUID.randomUUID().toString().substring(0, 8), "Produto", unit,
                new BigDecimal("20.00"), new BigDecimal("12.00"), new BigDecimal("10"), new BigDecimal("2"), null,
                true);
    }

    private User withoutCost(User.Role role) {
        User user = new User();
        user.setLogin("semcusto");
        user.setName("Sem custo");
        user.setRole(role);
        user.setPermissions(EnumSet.of(Permission.REGISTER_PRODUCTS, Permission.EDIT_PRODUCT_DATA,
                Permission.EDIT_PRICES, Permission.EDIT_STOCK_QUANTITY));
        return user;
    }

    private User saved(User user) {
        user.setLogin("semcusto_" + UUID.randomUUID());
        user.setPassword("x");
        user.setActive(true);
        return userRepository.save(user);
    }

    private Product stored(Product product) {
        return productRepository.findById(product.getId()).orElseThrow();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + ", veio " + actual);
    }

    @Test
    void editingWithoutCostPermissionChangesThePriceAndPreservesCostAndMinimumStock() {
        Product product = product(Product.Unidade.UN);
        User operator = saved(withoutCost(User.Role.OPERADOR));

        productService.update(product.getId(), "Novo nome", Product.Unidade.UN, new BigDecimal("22.00"), null, null,
                null, true, operator);

        assertMoney("22.00", stored(product).getPrice());
        assertMoney("12.00", stored(product).getCostPrice());
        assertMoney("2", stored(product).getMinStock());
    }

    @Test
    void switchingBetweenKiloAndHundredGramsConvertsTheHiddenCost() {
        Product product = product(Product.Unidade.KG);
        User operator = saved(withoutCost(User.Role.OPERADOR));

        productService.update(product.getId(), "Produto", Product.Unidade.G100, new BigDecimal("2.00"), null, null,
                null, true, operator);

        assertEquals(Product.Unidade.G100, stored(product).getUnit());
        assertMoney("1.20", stored(product).getCostPrice());
    }

    @Test
    void changingThePricingBasisCountsAsAPriceChange() {
        Product product = product(Product.Unidade.KG);
        User operator = withoutCost(User.Role.OPERADOR);
        operator.setPermissions(EnumSet.of(Permission.EDIT_PRODUCT_DATA));

        assertThrows(ManagerAuthorizationRequiredException.class, () -> productService.update(product.getId(),
                "Produto", Product.Unidade.G100, new BigDecimal("20.00"), null, null, null, true, operator));
    }

    @Test
    void aSubmittedCostDifferentFromTheStoredOneIsRefused() {
        Product product = product(Product.Unidade.UN);
        User operator = withoutCost(User.Role.OPERADOR);

        assertThrows(ManagerAuthorizationRequiredException.class, () -> productService.update(product.getId(),
                "Produto", Product.Unidade.UN, new BigDecimal("20.00"), new BigDecimal("1.00"), null, null, true,
                operator));
        assertThrows(ManagerAuthorizationRequiredException.class, () -> productService.update(product.getId(),
                "Produto", Product.Unidade.UN, new BigDecimal("20.00"), null, BigDecimal.ONE, null, true, operator));
        assertMoney("12.00", stored(product).getCostPrice());
    }

    @Test
    void bulkOperationsThatRevealOrChangeCostAreRefusedEvenForAManager() {
        Product product = product(Product.Unidade.UN);
        User manager = withoutCost(User.Role.GERENTE);
        List<Long> ids = List.of(product.getId());

        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyCost(ids, BigDecimal.ONE, manager));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyMarginPercent(ids, BigDecimal.TEN, manager));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applySuggestedPrice(ids, manager));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyMinStock(ids, BigDecimal.ONE, manager));
        assertMoney("20.00", stored(product).getPrice());
    }

    @Test
    void stockEntryWithCostAndPurchasesNeedTheCostPermission() {
        Product product = product(Product.Unidade.UN);
        User operator = withoutCost(User.Role.OPERADOR);

        assertThrows(IllegalArgumentException.class, () -> stockService.registerEntry(product.getCode(),
                BigDecimal.ONE, new BigDecimal("9.00"), "nota", operator));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> purchaseService.openPurchase(null, "NF", operator));
        assertMoney("10", stored(product).getQuantity());
    }
}
