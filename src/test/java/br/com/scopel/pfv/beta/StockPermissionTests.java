package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditLog;
import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.PermissionDefaults;
import br.com.scopel.pfv.beta.service.ProductBulkEditService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.StaffPermissions;
import br.com.scopel.pfv.beta.service.admin.AdminAccountService;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import br.com.scopel.pfv.beta.service.audit.AuthorizationTrail;
import br.com.scopel.pfv.beta.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class StockPermissionTests {

    @Autowired private ProductBulkEditService bulkEditService;
    @Autowired private StockService stockService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductService productService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private AdminAccountService accountService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

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
        user.setPermissions(br.com.scopel.pfv.beta.service.PermissionDefaults.forAccount(user.getLogin(), user.getName(), role));
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
    void anaClaraCannotRegisterEntriesBecauseTheyCarryCost() {
        Product p = product();
        User ana = named("Ana Clara", User.Role.OPERADOR);

        assertThrows(IllegalArgumentException.class,
                () -> stockService.registerEntry(p.getCode(), BigDecimal.ONE, null, "nota", ana));
        assertEquals(0, new BigDecimal("10").compareTo(productRepository.findById(p.getId()).orElseThrow().getQuantity()));
    }

    private BigDecimal priceOf(Product p) {
        return productRepository.findById(p.getId()).orElseThrow().getPrice();
    }

    private List<AuditLog> priceChangesOf(Product p) {
        return auditLogService.byPeriod(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1), 0, 200)
                .getContent().stream()
                .filter(log -> log.getAction() == AuditAction.PRICE_CHANGE && p.getId().equals(log.getEntityId()))
                .toList();
    }

    @Test
    void beatrizChangesTheSalePriceWithoutAManager() {
        Product p = product();

        bulkEditService.applyPrice(List.of(p.getId()), new BigDecimal("8.00"), named("Beatriz", User.Role.OPERADOR));

        assertEquals(0, new BigDecimal("8.00").compareTo(priceOf(p)));
    }

    @Test
    void anaClaraChangesThePriceWithoutAManagerButStillCannotSeeCost() {
        Product p = product();
        User ana = named("Ana Clara", User.Role.OPERADOR);

        bulkEditService.applyPrice(List.of(p.getId()), new BigDecimal("9.00"), ana);

        assertEquals(0, new BigDecimal("9.00").compareTo(priceOf(p)));
        assertTrue(StaffPermissions.canEditPrices(ana));
        assertFalse(StaffPermissions.canSeeCostFields(ana));
        assertFalse(StaffPermissions.canEditProductData(ana));
    }

    @Test
    void anAccountWithoutEditPricesStillNeedsAManagerForTheBulkAndFullEdit() {
        Product p = product();
        User joao = named("Joao", User.Role.OPERADOR);

        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> bulkEditService.applyPrice(List.of(p.getId()), new BigDecimal("6.00"), joao));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> productService.adjustPricesByPercent(BigDecimal.TEN, p.getCategory(), joao));
    }

    @Test
    void priceChangeIsAuditedWithPreviousNewAndTheOperator() {
        Product p = product();

        bulkEditService.applyPrice(List.of(p.getId()), new BigDecimal("7.25"), named("Beatriz", User.Role.OPERADOR));

        AuditLog log = priceChangesOf(p).get(0);
        assertEquals("5.00", log.getPreviousValue());
        assertEquals("7.25", log.getNewValue());
        assertEquals("Beatriz", log.getActorName());
    }

    @Test
    void managerAuthorizedPriceChangeRecordsWhoEditedAndWhoAuthorized() {
        Product p = product();
        User manager = named("Chefe", User.Role.GERENTE);

        AuthorizationTrail.begin(named("Maria", User.Role.OPERADOR));
        try {
            bulkEditService.applyPrice(List.of(p.getId()), new BigDecimal("6.10"), manager);
        } finally {
            AuthorizationTrail.end();
        }

        AuditLog log = priceChangesOf(p).get(0);
        assertEquals("Chefe", log.getActorName());
        assertTrue(log.getDescription().contains("editado por Maria, autorizado por Chefe"));
    }

    @Test
    void fullProductEditRefusesAPriceChangeFromWhoOnlyEditsProductData() {
        Product p = product();
        User joao = named("Joao", User.Role.OPERADOR);

        assertThrows(ManagerAuthorizationRequiredException.class, () -> productService.update(p.getId(),
                p.getDescription(), p.getUnit(), new BigDecimal("6.00"), new BigDecimal("1.00"), BigDecimal.ZERO,
                null, true, joao));
        assertEquals(0, new BigDecimal("5.00").compareTo(priceOf(p)));

        productService.update(p.getId(), "Outro nome", p.getUnit(), p.getPrice(), new BigDecimal("1.00"),
                BigDecimal.ZERO, null, true, joao);
        assertEquals("Outro nome", productRepository.findById(p.getId()).orElseThrow().getDescription());
    }

    @Test
    void devTurnsEditPricesOnAndOffForAnAccount() {
        User dev = new User();
        dev.setLogin("dev" + UUID.randomUUID().toString().substring(0, 8));
        dev.setName(dev.getLogin());
        dev.setPassword(passwordEncoder.encode("senha-dev"));
        dev.setRole(User.Role.DEV);
        dev = userRepository.save(dev);
        Long accountId = accountService.create(dev, "c" + UUID.randomUUID().toString().substring(0, 8), "senha123",
                User.Role.OPERADOR).id();

        accountService.setPermissions(dev, accountId, EnumSet.of(Permission.REGISTER_PRODUCTS, Permission.EDIT_PRICES));
        assertTrue(StaffPermissions.canEditPrices(userRepository.findById(accountId).orElseThrow()));

        accountService.setPermissions(dev, accountId, EnumSet.of(Permission.REGISTER_PRODUCTS));
        assertFalse(StaffPermissions.canEditPrices(userRepository.findById(accountId).orElseThrow()));
        assertTrue(PermissionDefaults.forAccount("Beatriz", "Beatriz", User.Role.OPERADOR).contains(Permission.EDIT_PRICES));
    }
}
