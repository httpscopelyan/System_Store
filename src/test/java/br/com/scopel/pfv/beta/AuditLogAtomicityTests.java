package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.ProductBulkEditService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.StockService;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class AuditLogAtomicityTests {

    @MockitoSpyBean private AuditLogService auditLogService;
    @Autowired private ProductService productService;
    @Autowired private ProductBulkEditService bulkEditService;
    @Autowired private StockService stockService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private User manager;
    private Product product;

    @BeforeEach
    void prepare() {
        User user = new User();
        user.setName("Gerente atômico");
        user.setLogin("atomic_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(User.Role.GERENTE);
        user.setActive(true);
        manager = userRepository.save(user);
        product = productService.create("atm " + UUID.randomUUID().toString().substring(0, 8), "Produto atômico",
                Product.Unidade.UN, new BigDecimal("5.00"), new BigDecimal("3.00"), new BigDecimal("20"),
                BigDecimal.ZERO, null, true, manager);
    }

    @AfterEach
    void removeCommittedTestData() {
        jdbc.update("delete from audit_log where actor_id = ?", manager.getId());
        jdbc.update("delete from stock_movement where product_id = ?", product.getId());
        jdbc.update("delete from product where id = ?", product.getId());
        jdbc.update("delete from users where id = ?", manager.getId());
    }

    private int auditRecordsOf(AuditAction action) {
        Integer count = jdbc.queryForObject(
                "select count(*) from audit_log where actor_id = ? and action = ?", Integer.class,
                manager.getId(), action.name());
        return count == null ? 0 : count;
    }

    private void failAuditRecordsOf(AuditAction action) {
        AuditLogService spiedTarget = AopTestUtils.getUltimateTargetObject(auditLogService);
        doThrow(new IllegalStateException("falha simulada na auditoria")).when(spiedTarget)
                .record(any(), eq(action), any(), any(), any(), any(), any());
    }

    private Product reloadedProduct() {
        return productRepository.findById(product.getId()).orElseThrow();
    }

    @Test
    void recordingOutsideATransactionIsRefusedSoNoChangeIsEverAuditedAlone() {
        assertThrows(IllegalTransactionStateException.class, () -> auditLogService.record(manager,
                AuditAction.PRICE_CHANGE, AuditEntityType.PRODUCT, product.getId(), "fora de transação", "1", "2"));
        assertEquals(0, auditRecordsOf(AuditAction.PRICE_CHANGE));
    }

    @Test
    void successfulChangeCommitsTheChangeAndItsAuditRecordTogether() {
        bulkEditService.applyPrice(List.of(product.getId()), new BigDecimal("9.99"), manager);

        assertEquals(0, new BigDecimal("9.99").compareTo(reloadedProduct().getPrice()));
        assertEquals(1, auditRecordsOf(AuditAction.PRICE_CHANGE));
        assertEquals(1, auditRecordsOf(AuditAction.PRODUCT_CREATED));
    }

    @Test
    void whenTheChangeFailsItsAuditRecordIsRolledBackToo() {
        String newCode = "atn " + UUID.randomUUID().toString().substring(0, 8);

        assertThrows(IllegalArgumentException.class, () -> productService.update(product.getId(), newCode, "  ",
                Product.Unidade.UN, new BigDecimal("9.00"), new BigDecimal("3.00"), BigDecimal.ZERO, null, true,
                manager));

        assertEquals(product.getCode(), reloadedProduct().getCode());
        assertEquals(0, auditRecordsOf(AuditAction.PRODUCT_UPDATED));
        assertEquals(0, auditRecordsOf(AuditAction.PRICE_CHANGE));
    }

    @Test
    void whenTheAuditRecordFailsThePriceChangeDoesNotHappen() {
        failAuditRecordsOf(AuditAction.PRICE_CHANGE);

        assertThrows(IllegalStateException.class,
                () -> bulkEditService.applyPrice(List.of(product.getId()), new BigDecimal("9.99"), manager));

        assertEquals(0, new BigDecimal("5.00").compareTo(reloadedProduct().getPrice()));
        assertEquals(0, auditRecordsOf(AuditAction.PRICE_CHANGE));
    }

    @Test
    void whenTheAuditRecordFailsTheStockAdjustmentAndItsMovementDoNotHappen() {
        failAuditRecordsOf(AuditAction.STOCK_ADJUSTMENT);

        assertThrows(IllegalStateException.class,
                () -> stockService.adjust(product.getCode(), new BigDecimal("3"), "contagem", manager));

        assertEquals(0, new BigDecimal("20").compareTo(reloadedProduct().getQuantity()));
        assertEquals(0, stockService.history(product.getCode()).size());
        assertEquals(0, auditRecordsOf(AuditAction.STOCK_ADJUSTMENT));
    }
}
