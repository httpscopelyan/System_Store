package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.AuditLog;
import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ProductBulkEditService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.StockService;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AuditLogTests {

    @Autowired private AuditLogService auditLogService;
    @Autowired private StockService stockService;
    @Autowired private ProductService productService;
    @Autowired private ProductBulkEditService bulkEditService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private UserRepository userRepository;

    private User manager;
    private User anaClara;
    private User dayana;
    private Product product;

    private User persistedUser(String name, User.Role role) {
        User user = new User();
        user.setName(name);
        user.setLogin("audit_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    @BeforeEach
    void prepare() {
        manager = persistedUser("Gerente auditado", User.Role.GERENTE);
        anaClara = persistedUser("Ana Clara", User.Role.OPERADOR);
        dayana = persistedUser("Dayana", User.Role.OPERADOR);
        product = productService.create("aud " + UUID.randomUUID().toString().substring(0, 8), "Produto auditado",
                Product.Unidade.UN, new BigDecimal("5.00"), new BigDecimal("3.00"), new BigDecimal("20"),
                BigDecimal.ZERO, null, true, manager);
    }

    private List<AuditLog> recordsOf(User actor) {
        return auditLogService.byActor(actor.getId(), 0, 50).getContent();
    }

    private AuditLog onlyRecordOf(User actor, AuditAction action) {
        List<AuditLog> matching = recordsOf(actor).stream().filter(log -> log.getAction() == action).toList();
        assertEquals(1, matching.size(), "registros de " + action + " de " + actor.getName());
        return matching.get(0);
    }

    @Test
    void productCreationIsRecordedWithItsCreator() {
        AuditLog creation = onlyRecordOf(manager, AuditAction.PRODUCT_CREATED);

        assertEquals(AuditEntityType.PRODUCT, creation.getEntityType());
        assertEquals(product.getId(), creation.getEntityId());
        assertEquals("Gerente auditado", creation.getActorName());
        assertEquals(manager.getId(), creation.getActor().getId());
        assertNull(creation.getPreviousValue());
        assertTrue(creation.getNewValue().contains("Preço 5.00"));
        assertTrue(creation.getDescription().contains("Produto auditado"));
        assertNotNull(creation.getOccurredAt());
    }

    @Test
    void stockAdjustmentRecordsThePreviousAndTheNewQuantity() {
        stockService.adjust(product.getCode(), new BigDecimal("12"), "contagem de inventário", dayana);

        AuditLog adjustment = onlyRecordOf(dayana, AuditAction.STOCK_ADJUSTMENT);
        assertEquals("20", adjustment.getPreviousValue());
        assertEquals("12", adjustment.getNewValue());
        assertEquals(product.getId(), adjustment.getEntityId());
        assertTrue(adjustment.getDescription().contains("contagem de inventário"));
    }

    @Test
    void adjustmentToTheSameQuantityRecordsNothing() {
        stockService.adjust(product.getCode(), new BigDecimal("20.000"), "recontagem igual", dayana);

        assertTrue(recordsOf(dayana).isEmpty());
    }

    @Test
    void anaClaraQuantityEditsInlineAndInBulkAreRecordedUnderHerName() {
        stockService.adjustQuantityInline(product.getCode(), new BigDecimal("18"), anaClara);
        bulkEditService.applyStockQuantity(List.of(product.getId()), new BigDecimal("7.5"), anaClara);

        List<AuditLog> quantityEdits = recordsOf(anaClara);
        assertEquals(2, quantityEdits.size());
        assertEquals("18", quantityEdits.get(0).getPreviousValue());
        assertEquals("7.5", quantityEdits.get(0).getNewValue());
        assertEquals("20", quantityEdits.get(1).getPreviousValue());
        assertEquals("18", quantityEdits.get(1).getNewValue());
        assertEquals("Ana Clara", quantityEdits.get(0).getActorName());
        assertEquals(AuditAction.STOCK_ADJUSTMENT, quantityEdits.get(0).getAction());
    }

    @Test
    void anaClaraIsRefusedByTheServicesWhenChangingPriceOrRegisteringProducts() {
        String newCode = "ana " + UUID.randomUUID().toString().substring(0, 8);
        Product unsaved = new Product();
        unsaved.setCode(newCode);
        unsaved.setDescription("Cadastro rápido");
        unsaved.setPrice(BigDecimal.ONE);
        unsaved.setQuantity(BigDecimal.ONE);
        unsaved.setUnit(Product.Unidade.UN);

        assertThrows(ManagerAuthorizationRequiredException.class, () -> productService.create(newCode, "Novo",
                Product.Unidade.UN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, null, true,
                anaClara));
        assertThrows(ManagerAuthorizationRequiredException.class, () -> productService.save(unsaved, anaClara));
        assertThrows(ManagerAuthorizationRequiredException.class, () -> productService.update(product.getId(),
                "Outro nome", Product.Unidade.UN, new BigDecimal("9.00"), new BigDecimal("3.00"), BigDecimal.ZERO,
                null, true, anaClara));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> productService.changeCode(product.getId(), newCode, anaClara));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> productService.modificationPrice(product.getCode(), new BigDecimal("9.00"), anaClara));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> productService.adjustPricesByPercent(BigDecimal.TEN, "categoria-inexistente", anaClara));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyPrice(List.of(product.getId()), new BigDecimal("9.00"), anaClara));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyDescription(List.of(product.getId()), "Outro nome", anaClara));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyMinStock(List.of(product.getId()), BigDecimal.ONE, anaClara));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyCode(product.getId(), newCode, anaClara));

        Product reloaded = productService.findById(product.getId());
        assertEquals(0, new BigDecimal("5.00").compareTo(reloaded.getPrice()));
        assertEquals("Produto auditado", reloaded.getDescription());
        assertTrue(recordsOf(anaClara).isEmpty());
    }

    @Test
    void stockEntryRecordsTheQuantityAndTheCostChange() {
        stockService.registerEntry(product.getCode(), new BigDecimal("5"), new BigDecimal("4.00"), "nota 123", dayana);

        AuditLog entry = onlyRecordOf(dayana, AuditAction.STOCK_ENTRY);
        AuditLog cost = onlyRecordOf(dayana, AuditAction.COST_CHANGE);
        assertEquals("20", entry.getPreviousValue());
        assertEquals("25", entry.getNewValue());
        assertEquals("3.00", cost.getPreviousValue());
        assertEquals("4.00", cost.getNewValue());
    }

    @Test
    void stockRectificationIsRecordedWithTheAuthorizingManager() {
        stockService.rectify(product.getCode(), new BigDecimal("15"), "erro de contagem", manager);

        AuditLog rectification = onlyRecordOf(manager, AuditAction.STOCK_RECTIFICATION);
        assertEquals("20", rectification.getPreviousValue());
        assertEquals("15", rectification.getNewValue());
        assertTrue(rectification.getDescription().startsWith("Retificação de estoque"));
    }

    @Test
    void priceAndCostChangesInBulkAreRecordedPerProduct() {
        bulkEditService.applyPrice(List.of(product.getId()), new BigDecimal("7.495"), dayana);
        bulkEditService.applyCost(List.of(product.getId()), new BigDecimal("3.50"), manager);
        bulkEditService.applyPrice(List.of(product.getId()), new BigDecimal("7.50"), dayana);

        AuditLog price = onlyRecordOf(dayana, AuditAction.PRICE_CHANGE);
        AuditLog cost = onlyRecordOf(manager, AuditAction.COST_CHANGE);
        assertEquals("5.00", price.getPreviousValue());
        assertEquals("7.50", price.getNewValue());
        assertEquals("3.00", cost.getPreviousValue());
        assertEquals("3.50", cost.getNewValue());
    }

    @Test
    void productEditRecordsOnlyTheFieldsThatChanged() {
        productService.update(product.getId(), product.getCode(), "Produto renomeado", Product.Unidade.UN,
                new BigDecimal("6.00"), new BigDecimal("3.00"), new BigDecimal("2"), "Bebidas", true, dayana);

        List<AuditLog> edits = recordsOf(dayana);
        assertEquals(4, edits.size());
        assertEquals("6.00", onlyRecordOf(dayana, AuditAction.PRICE_CHANGE).getNewValue());
        List<AuditLog> dataEdits = edits.stream()
                .filter(log -> log.getAction() == AuditAction.PRODUCT_UPDATED).toList();
        assertEquals(3, dataEdits.size());
        assertTrue(dataEdits.stream().anyMatch(log -> "Produto auditado".equals(log.getPreviousValue())
                && "Produto renomeado".equals(log.getNewValue())));
        assertTrue(dataEdits.stream().anyMatch(log -> log.getPreviousValue() == null
                && "Bebidas".equals(log.getNewValue())));
        assertTrue(dataEdits.stream().anyMatch(log -> "0".equals(log.getPreviousValue())
                && "2".equals(log.getNewValue())));
    }

    @Test
    void codeChangeAndInactivationAreRecorded() {
        String newCode = "novo " + UUID.randomUUID().toString().substring(0, 8);
        String previousCode = product.getCode();
        bulkEditService.applyCode(product.getId(), newCode, dayana);
        productService.inactivate(newCode, manager);

        AuditLog codeChange = onlyRecordOf(dayana, AuditAction.PRODUCT_UPDATED);
        assertEquals(previousCode, codeChange.getPreviousValue());
        assertEquals(newCode, codeChange.getNewValue());
        AuditLog inactivation = onlyRecordOf(manager, AuditAction.PRODUCT_UPDATED);
        assertEquals("Ativo", inactivation.getPreviousValue());
        assertEquals("Inativo", inactivation.getNewValue());
    }

    @Test
    void cashRectificationIsRecordedWithTheOriginalAndTheCorrectedAmount() {
        CashRegister register = cashRegisterService.open(dayana, new BigDecimal("100.00"));
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("40"), "Fornecedor");

        cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("10"), "erro de digitação", manager);

        AuditLog rectification = onlyRecordOf(manager, AuditAction.CASH_RECTIFICATION);
        assertEquals(AuditEntityType.CASH_MOVEMENT, rectification.getEntityType());
        assertEquals(withdrawal.getId(), rectification.getEntityId());
        assertEquals("40.00", rectification.getPreviousValue());
        assertEquals("10.00", rectification.getNewValue());
        assertTrue(rectification.getDescription().contains("erro de digitação"));
    }

    @Test
    void changeWithoutAnIdentifiedUserIsRecordedAsSystem() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        bulkEditService.applyMinStock(List.of(product.getId()), new BigDecimal("4"));

        AuditLog systemRecord = auditLogService.byPeriod(before, LocalDateTime.now().plusSeconds(1), 0, 50)
                .getContent().stream()
                .filter(log -> product.getId().equals(log.getEntityId()) && log.getActor() == null)
                .findFirst().orElseThrow();
        assertEquals(AuditLogService.SYSTEM_ACTOR_NAME, systemRecord.getActorName());
        assertEquals("4", systemRecord.getNewValue());
    }

    @Test
    void historyIsPagedNewestFirstAndFilteredByActorAndPeriod() {
        for (int quantity = 1; quantity <= 5; quantity++) {
            stockService.adjust(product.getCode(), new BigDecimal(quantity), "contagem " + quantity, dayana);
        }
        LocalDateTime now = LocalDateTime.now();

        Page<AuditLog> firstPage = auditLogService.byActor(dayana.getId(), 0, 2);
        Page<AuditLog> lastPage = auditLogService.byActor(dayana.getId(), 2, 2);

        assertEquals(5, firstPage.getTotalElements());
        assertEquals(3, firstPage.getTotalPages());
        assertEquals("5", firstPage.getContent().get(0).getNewValue());
        assertEquals("4", firstPage.getContent().get(1).getNewValue());
        assertEquals(1, lastPage.getContent().size());
        assertEquals("1", lastPage.getContent().get(0).getNewValue());
        assertEquals(5, auditLogService.byActorAndPeriod(dayana.getId(), now.minusMinutes(1), now.plusMinutes(1),
                0, 50).getTotalElements());
        assertEquals(0, auditLogService.byActorAndPeriod(dayana.getId(), now.minusDays(2), now.minusDays(1),
                0, 50).getTotalElements());
        assertEquals(0, auditLogService.byActorAndPeriod(anaClara.getId(), now.minusMinutes(1), now.plusMinutes(1),
                0, 50).getTotalElements());
        assertTrue(auditLogService.byPeriod(now.minusMinutes(1), now.plusMinutes(1), 0, 50).getTotalElements() >= 6);
        assertEquals(dayana.getId(), auditLogService.recent(0, 1).getContent().get(0).getActor().getId());
    }

    @Test
    void invalidQueriesAndIncompleteRecordsAreRefused() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () -> auditLogService.recent(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> auditLogService.recent(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> auditLogService.recent(0, AuditLogService.MAX_PAGE_SIZE + 1));
        assertThrows(IllegalArgumentException.class, () -> auditLogService.byActor(null, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> auditLogService.byPeriod(now, now.minusDays(1), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> auditLogService.byPeriod(null, now, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> auditLogService.record(manager, null,
                AuditEntityType.PRODUCT, product.getId(), "sem ação", null, null));
        assertThrows(IllegalArgumentException.class, () -> auditLogService.record(manager,
                AuditAction.PRICE_CHANGE, AuditEntityType.PRODUCT, product.getId(), "  ", null, null));
    }
}
