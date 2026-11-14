package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.admin.AdminAccount;
import br.com.scopel.pfv.beta.service.admin.AdminAccountService;
import br.com.scopel.pfv.beta.service.admin.AdminCashGroupService;
import br.com.scopel.pfv.beta.service.admin.AdminHistoryService;
import br.com.scopel.pfv.beta.service.admin.HistoryPage;
import br.com.scopel.pfv.beta.service.admin.HistoryTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AdminAdministrationTests {

    @Autowired private AdminAccountService accountService;
    @Autowired private AdminCashGroupService cashGroupService;
    @Autowired private AdminHistoryService historyService;
    @Autowired private ProductService productService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User dev;

    @BeforeEach
    void createDev() {
        dev = new User();
        dev.setLogin("dev" + UUID.randomUUID().toString().substring(0, 8));
        dev.setName(dev.getLogin());
        dev.setPassword(passwordEncoder.encode("senha-dev"));
        dev.setRole(User.Role.DEV);
        dev = userRepository.save(dev);
    }

    private String uniqueName() {
        return "u" + UUID.randomUUID().toString().substring(0, 8);
    }

    private User anaLikeUser() {
        AdminAccount created = accountService.create(dev, uniqueName(), "senha123", User.Role.OPERADOR);
        accountService.setPermissions(dev, created.id(), EnumSet.of(Permission.REGISTER_PRODUCTS, Permission.EDIT_STOCK_QUANTITY));
        return userRepository.findById(created.id()).orElseThrow();
    }

    @Test
    void onlyDevCanUseTheAdministrationServices() {
        User manager = new User();
        manager.setRole(User.Role.GERENTE);
        manager.setLogin("gerente");
        assertThrows(ManagerAuthorizationRequiredException.class, () -> accountService.accounts(manager));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> historyService.page(manager, HistoryTab.AUDIT, null, null, 0));
        assertThrows(ManagerAuthorizationRequiredException.class, () -> cashGroupService.groups(null));
    }

    @Test
    void createdOperatorStartsWithBasicPermissionsAndAPasswordHash() {
        AdminAccount created = accountService.create(dev, uniqueName(), "senha123", User.Role.OPERADOR);
        User stored = userRepository.findById(created.id()).orElseThrow();
        assertEquals(EnumSet.of(Permission.REGISTER_PRODUCTS, Permission.SEE_COST_FIELDS, Permission.EDIT_PRODUCT_DATA),
                stored.getPermissions());
        assertTrue(stored.getPassword().startsWith("$2"));
    }

    @Test
    void devAccountIsProtectedAndCannotBeCreatedHere() {
        assertThrows(IllegalArgumentException.class, () -> accountService.create(dev, uniqueName(), "senha123", User.Role.DEV));
        assertThrows(IllegalArgumentException.class, () -> accountService.setActive(dev, dev.getId(), false));
        assertThrows(IllegalArgumentException.class, () -> accountService.delete(dev, dev.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> accountService.setPermissions(dev, dev.getId(), Set.of(Permission.EDIT_PRICES)));
    }

    @Test
    void inactiveAccountsCannotLogIn() {
        String name = uniqueName();
        AdminAccount created = accountService.create(dev, name, "senha123", User.Role.OPERADOR);
        accountService.setActive(dev, created.id(), false);
        assertFalse(userRepository.findById(created.id()).orElseThrow().getActive());
    }

    @Test
    void operatorWithoutCostPermissionRegistersProductsOnlyWithoutCostFields() {
        User ana = anaLikeUser();
        String code = "T" + UUID.randomUUID().toString().substring(0, 8);
        Product created = productService.create(code, "Produto da Ana", Product.Unidade.UN, new BigDecimal("4.50"),
                BigDecimal.ZERO, new BigDecimal("10"), BigDecimal.ZERO, "Bebidas", true, ana);
        assertNotNull(created.getId());
        assertThrows(ManagerAuthorizationRequiredException.class, () -> productService.create(
                code + "x", "Com custo", Product.Unidade.UN, new BigDecimal("4.50"),
                new BigDecimal("2.00"), BigDecimal.ONE, BigDecimal.ZERO, null, true, ana));
        assertThrows(ManagerAuthorizationRequiredException.class, () -> productService.create(
                code + "y", "Com minimo", Product.Unidade.UN, new BigDecimal("4.50"),
                BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("3"), null, true, ana));
    }

    @Test
    void permissionChangeIsAuditedAndShownInTheHistory() {
        User target = anaLikeUser();
        HistoryPage page = historyService.page(dev, HistoryTab.AUDIT, null, "Permissões de " + target.getLogin(), 0);
        assertEquals(1, page.total());
        assertEquals("USER_PERMISSIONS_CHANGED", page.rows().get(0).get(page.columns().indexOf("Ação")));
        assertEquals(dev.getName(), page.rows().get(0).get(page.columns().indexOf("Quem")));
    }

    @Test
    void everyHistoryTabQueryRuns() {
        for (HistoryTab tab : HistoryTab.values()) {
            HistoryPage page = historyService.page(dev, tab, null, null, 0);
            assertFalse(page.columns().isEmpty(), tab.name());
        }
    }

    @Test
    void historySearchTreatsPercentSignsLiterally() {
        assertEquals(0, historyService.page(dev, HistoryTab.AUDIT, null, "%__nunca_existe__%", 0).total());
    }

    @Test
    void cashGroupSharesTheOpenRegisterBetweenMembersAndDissolveSeparatesThem() {
        User first = anaLikeUser();
        User second = anaLikeUser();
        cashGroupService.save(dev, null, "Grupo " + uniqueName(), Set.of(first.getId(), second.getId()));
        CashRegister opened = cashRegisterService.open(first, new BigDecimal("50.00"));
        assertEquals(opened.getId(), cashRegisterService.findOpenByOperator(second).orElseThrow().getId());

        Long groupId = userRepository.findById(first.getId()).orElseThrow().getCashGroup().getId();
        cashGroupService.dissolve(dev, groupId);
        assertTrue(cashRegisterService.findOpenByOperator(second).isEmpty());
        assertEquals(opened.getId(), cashRegisterService.findOpenByOperator(first).orElseThrow().getId());
    }

    @Test
    void cashGroupNeedsTwoMembersAndAnAccountBelongsToOneGroupOnly() {
        User first = anaLikeUser();
        User second = anaLikeUser();
        User third = anaLikeUser();
        assertThrows(IllegalArgumentException.class,
                () -> cashGroupService.save(dev, null, uniqueName(), Set.of(first.getId())));
        cashGroupService.save(dev, null, "A " + uniqueName(), Set.of(first.getId(), second.getId()));
        cashGroupService.save(dev, null, "B " + uniqueName(), Set.of(second.getId(), third.getId()));
        Long groupOfSecond = userRepository.findById(second.getId()).orElseThrow().getCashGroup().getId();
        assertEquals(groupOfSecond, userRepository.findById(third.getId()).orElseThrow().getCashGroup().getId());
        assertNotEquals(userRepository.findById(first.getId()).orElseThrow().getCashGroup().getId(), groupOfSecond);
    }
}
