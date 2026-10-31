package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.ProductBulkEditService;
import br.com.scopel.pfv.beta.service.ProductBulkEditService.BulkResult;
import br.com.scopel.pfv.beta.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
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
class ProductBulkEditServiceTests {

    @Autowired private ProductBulkEditService bulkEditService;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private AppSettingService appSettingService;

    private User manager;
    private User operator;
    private Product monsterA;
    private Product monsterB;
    private Product monsterC;
    private Product noCost;

    @BeforeEach
    void prepare() {
        manager = new User();
        manager.setRole(User.Role.GERENTE);
        operator = new User();
        operator.setRole(User.Role.OPERADOR);
        monsterA = product("Monster A", "10.00", "7.00");
        monsterB = product("Monster B", "10.00", "8.00");
        monsterC = product("Monster C", "10.00", "5.00");
        noCost = product("Sem custo", "10.00", "0.00");
    }

    private Product product(String description, String price, String cost) {
        return productService.create("bulk " + UUID.randomUUID().toString().substring(0, 8), description,
                Product.Unidade.UN, new BigDecimal(price), new BigDecimal(cost), new BigDecimal("10"),
                BigDecimal.ZERO, null, true);
    }

    private List<Long> ids(Product... products) {
        return java.util.Arrays.stream(products).map(Product::getId).toList();
    }

    private Product reload(Product product) {
        return productRepository.findById(product.getId()).orElseThrow();
    }

    @Test
    void priceIsAppliedToEverySelectedProduct_RN26() {
        BulkResult result = bulkEditService.applyPrice(ids(monsterA, monsterB, monsterC), new BigDecimal("9.99"), manager);
        assertEquals(3, result.changed());
        assertEquals(new BigDecimal("9.99"), reload(monsterA).getPrice());
        assertEquals(new BigDecimal("9.99"), reload(monsterB).getPrice());
        assertEquals(new BigDecimal("9.99"), reload(monsterC).getPrice());
        assertEquals(new BigDecimal("10.00"), reload(noCost).getPrice());
    }

    @Test
    void priceIsRoundedHalfUpToTwoPlaces_RN26() {
        bulkEditService.applyPrice(ids(monsterA), new BigDecimal("9.995"), manager);
        assertEquals(new BigDecimal("10.00"), reload(monsterA).getPrice());
    }

    @Test
    void negativeValuesAreRefused_RN26() {
        assertThrows(IllegalArgumentException.class,
                () -> bulkEditService.applyPrice(ids(monsterA), new BigDecimal("-1"), manager));
        assertThrows(IllegalArgumentException.class,
                () -> bulkEditService.applyCost(ids(monsterA), new BigDecimal("-1"), manager));
        assertThrows(IllegalArgumentException.class,
                () -> bulkEditService.applyMinStock(ids(monsterA), new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> bulkEditService.applyMarginPercent(ids(monsterA), new BigDecimal("-1"), manager));
    }

    @Test
    void priceCostMarginAndSuggestionRequireAManager_RN26() {
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyPrice(ids(monsterA), BigDecimal.ONE, operator));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyCost(ids(monsterA), BigDecimal.ONE, operator));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyMarginPercent(ids(monsterA), BigDecimal.ONE, operator));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applySuggestedPrice(ids(monsterA), operator));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyPrice(ids(monsterA), BigDecimal.ONE, null));
        assertEquals(new BigDecimal("10.00"), reload(monsterA).getPrice());
    }

    @Test
    void minimumStockDoesNotRequireAManager_RN26() {
        BulkResult result = bulkEditService.applyMinStock(ids(monsterA, monsterB), new BigDecimal("12"));
        assertEquals(2, result.changed());
        assertEquals(0, new BigDecimal("12").compareTo(reload(monsterA).getMinStock()));
    }

    @Test
    void unknownIdsAreIgnored_RN26() {
        BulkResult result = bulkEditService.applyPrice(List.of(monsterA.getId(), -99L), new BigDecimal("3.00"), manager);
        assertEquals(1, result.changed());
    }

    @Test
    void costIsAppliedAndOnlyTheCostChanges_RN26() {
        bulkEditService.applyCost(ids(monsterA, monsterB), new BigDecimal("4.50"), manager);
        assertEquals(new BigDecimal("4.50"), reload(monsterA).getCostPrice());
        assertEquals(new BigDecimal("10.00"), reload(monsterA).getPrice());
    }

    @Test
    void marginIsAppliedOverEachProductsOwnCost_RN26() {
        BulkResult result = bulkEditService.applyMarginPercent(ids(monsterA, monsterB, monsterC), new BigDecimal("50"), manager);
        assertEquals(3, result.changed());
        assertEquals(new BigDecimal("10.50"), reload(monsterA).getPrice());
        assertEquals(new BigDecimal("12.00"), reload(monsterB).getPrice());
        assertEquals(new BigDecimal("7.50"), reload(monsterC).getPrice());
    }

    @Test
    void productsWithoutCostAreSkippedWhenApplyingMargin_RN26() {
        BulkResult result = bulkEditService.applyMarginPercent(ids(monsterA, noCost), new BigDecimal("50"), manager);
        assertEquals(1, result.changed());
        assertEquals(1, result.skipped());
        assertEquals(new BigDecimal("10.00"), reload(noCost).getPrice());
    }

    @Test
    void acceptingTheSuggestionRestoresTheComputedPrice_RF63() {
        appSettingService.set(AppSettingService.PRICING_DEFAULT_MARGIN_PERCENT, "30");
        BulkResult result = bulkEditService.applySuggestedPrice(ids(monsterA, monsterB, noCost), manager);
        assertEquals(2, result.changed());
        assertEquals(1, result.skipped());
        assertEquals(new BigDecimal("9.10"), reload(monsterA).getPrice());
        assertEquals(new BigDecimal("10.40"), reload(monsterB).getPrice());
        assertEquals(new BigDecimal("10.00"), reload(noCost).getPrice());
    }

    @Test
    void suggestionUsesTheConfiguredDefaultMargin_RF63() {
        appSettingService.set(AppSettingService.PRICING_DEFAULT_MARGIN_PERCENT, "100");
        bulkEditService.applySuggestedPrice(ids(monsterA), manager);
        assertEquals(new BigDecimal("14.00"), reload(monsterA).getPrice());
    }

    @Test
    void descriptionMustNotBeBlank_RN26() {
        assertThrows(IllegalArgumentException.class, () -> bulkEditService.applyDescription(ids(monsterA), "  "));
        bulkEditService.applyDescription(ids(monsterA), "  Monster Zero  ");
        assertEquals("Monster Zero", reload(monsterA).getDescription());
    }

    @Test
    void stockQuantityIsNeverTouchedByBulkEdits_RN26() {
        BigDecimal before = reload(monsterA).getQuantity();
        bulkEditService.applyPrice(ids(monsterA), new BigDecimal("1.00"), manager);
        bulkEditService.applyCost(ids(monsterA), new BigDecimal("1.00"), manager);
        bulkEditService.applyMinStock(ids(monsterA), new BigDecimal("1"));
        assertEquals(0, before.compareTo(reload(monsterA).getQuantity()));
    }
}
