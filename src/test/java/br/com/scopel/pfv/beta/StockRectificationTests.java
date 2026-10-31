package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.MissingReasonException;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.StockMovement;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class StockRectificationTests {

    @Autowired private StockService stockService;
    @Autowired private ProductService productService;
    @Autowired private UserRepository userRepository;

    private User manager;
    private User operator;
    private String code;

    private User persistedUser(User.Role role) {
        User user = new User();
        user.setName(role.name());
        user.setLogin(role.name().toLowerCase() + "_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    @BeforeEach
    void prepare() {
        manager = persistedUser(User.Role.GERENTE);
        operator = persistedUser(User.Role.OPERADOR);
        code = "rect " + UUID.randomUUID().toString().substring(0, 8);
        productService.create(code, "Produto retificado", Product.Unidade.UN, new BigDecimal("5.00"),
                new BigDecimal("3.00"), new BigDecimal("20"), BigDecimal.ZERO, null, true);
    }

    @Test
    void adjustWithoutReasonIsRefused_RN28() {
        assertThrows(MissingReasonException.class, () -> stockService.adjust(code, new BigDecimal("5"), "", null));
        assertThrows(MissingReasonException.class, () -> stockService.adjust(code, new BigDecimal("5"), "ab", null));
        assertThrows(MissingReasonException.class, () -> stockService.adjust(code, new BigDecimal("5"), null, null));
    }

    @Test
    void adjustWithReasonStillWorks_RN28() {
        Product after = stockService.adjust(code, new BigDecimal("12"), "contagem de inventário", null);
        assertEquals(0, new BigDecimal("12").compareTo(after.getQuantity()));
    }

    @Test
    void rectifyLandsOnTheCorrectedQuantityAndKeepsTheHistory_RN29() {
        stockService.adjust(code, new BigDecimal("50"), "contagem errada", manager);
        Product after = stockService.rectify(code, new BigDecimal("15"), "erro de contagem", manager);
        assertEquals(0, new BigDecimal("15").compareTo(after.getQuantity()));
        List<StockMovement> history = stockService.history(code);
        assertEquals(2, history.size());
        assertEquals(StockMovement.Type.RETIFICACAO, history.get(0).getType());
        assertEquals(StockMovement.Type.AJUSTE, history.get(1).getType());
        assertEquals(0, new BigDecimal("50").compareTo(history.get(1).getResultingStock()));
    }

    @Test
    void rectificationRecordsReasonAuthorizerAndResultingStock_RN29() {
        stockService.adjust(code, new BigDecimal("50"), "contagem errada", manager);
        stockService.rectify(code, new BigDecimal("15"), "erro de contagem", manager);
        StockMovement rectification = stockService.history(code).get(0);
        assertEquals("erro de contagem", rectification.getReason());
        assertEquals(manager, rectification.getAuthorizedBy());
        assertEquals(0, new BigDecimal("15").compareTo(rectification.getResultingStock()));
        assertEquals(0, new BigDecimal("35").compareTo(rectification.getQuantity()));
        assertNotNull(rectification.getCorrectedMovement());
        assertEquals(StockMovement.Type.AJUSTE, rectification.getCorrectedMovement().getType());
    }

    @Test
    void operatorCannotRectify_RN29() {
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> stockService.rectify(code, new BigDecimal("1"), "erro", operator));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> stockService.rectify(code, new BigDecimal("1"), "erro", null));
    }

    @Test
    void rectificationNeedsAReason_RN29() {
        assertThrows(MissingReasonException.class, () -> stockService.rectify(code, new BigDecimal("1"), " ", manager));
    }

    @Test
    void negativeCorrectedQuantityIsRefused_RN29() {
        assertThrows(IllegalArgumentException.class,
                () -> stockService.rectify(code, new BigDecimal("-1"), "negativo", manager));
    }

    @Test
    void unknownProductIsRefused_RN29() {
        assertThrows(ProductNotfindException.class,
                () -> stockService.rectify("nao-existe-" + UUID.randomUUID(), BigDecimal.ONE, "erro", manager));
    }

    @Test
    void rectifyingWithoutPriorMovementsStillRecordsTheTrail_RN29() {
        stockService.rectify(code, new BigDecimal("18"), "recontagem", manager);
        List<StockMovement> history = stockService.history(code);
        assertEquals(1, history.size());
        assertEquals(StockMovement.Type.RETIFICACAO, history.get(0).getType());
    }
}
