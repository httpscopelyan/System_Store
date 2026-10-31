package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.InvalidQuantityException;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.SaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SaleQuantityRulesTests {

    @Autowired private SaleService saleService;
    @Autowired private ProductService productService;
    @Autowired private AppSettingService appSettingService;
    @Autowired private UserRepository userRepository;
    @Autowired private CashRegisterService cashRegisterService;

    private Sale sale;
    private String unitCode;
    private String weightCode;

    @BeforeEach
    void prepare() {
        User operator = new User();
        operator.setName("Operador");
        operator.setLogin("qty_" + UUID.randomUUID());
        operator.setPassword("x");
        operator.setRole(User.Role.OPERADOR);
        operator.setActive(true);
        operator = userRepository.save(operator);
        CashRegister cashRegister = cashRegisterService.open(operator, new BigDecimal("10.00"));
        sale = saleService.openSale(operator, cashRegister);
        unitCode = "qty " + UUID.randomUUID().toString().substring(0, 8);
        weightCode = "kg " + UUID.randomUUID().toString().substring(0, 8);
        productService.create(unitCode, "Unitário", Product.Unidade.UN, new BigDecimal("2.00"),
                BigDecimal.ONE, new BigDecimal("5000"), BigDecimal.ZERO, null, true);
        productService.create(weightCode, "Pesável", Product.Unidade.KG, new BigDecimal("39.90"),
                BigDecimal.ONE, new BigDecimal("5000"), BigDecimal.ZERO, null, true);
    }

    @Test
    void quantityAboveTheDefaultLimitIsRefused_RN24() {
        assertThrows(InvalidQuantityException.class,
                () -> saleService.addItem(sale, unitCode, new BigDecimal("1000")));
    }

    @Test
    void quantityExactlyAtTheLimitIsAccepted_RN24() {
        Sale updated = saleService.addItem(sale, unitCode, new BigDecimal("999"));
        assertEquals(0, new BigDecimal("999").compareTo(updated.getItems().get(0).getQuantity()));
    }

    @Test
    void limitComesFromSettings_RN24() {
        appSettingService.set(AppSettingService.SALE_MAX_QUANTITY_PER_ITEM, "20");
        assertThrows(InvalidQuantityException.class,
                () -> saleService.addItem(sale, unitCode, new BigDecimal("21")));
        assertEquals(0, new BigDecimal("20").compareTo(saleService.maxQuantityPerItem()));
    }

    @Test
    void invalidConfiguredLimitFallsBackToTheDefault_RN24() {
        appSettingService.set(AppSettingService.SALE_MAX_QUANTITY_PER_ITEM, "abc");
        assertEquals(0, new BigDecimal("999").compareTo(saleService.maxQuantityPerItem()));
        appSettingService.set(AppSettingService.SALE_MAX_QUANTITY_PER_ITEM, "0");
        assertEquals(0, new BigDecimal("999").compareTo(saleService.maxQuantityPerItem()));
    }

    @Test
    void weightAboveTheLimitIsRefusedToo_RN24() {
        assertThrows(InvalidQuantityException.class,
                () -> saleService.addItem(sale, weightCode, new BigDecimal("1500.000")));
    }

    @Test
    void quantityAskingAfterScanIsOnByDefaultAndConfigurable_RN24() {
        assertTrue(saleService.asksQuantityAfterScan());
        appSettingService.set(AppSettingService.SALE_ASK_QUANTITY_AFTER_SCAN, "false");
        assertEquals(false, saleService.asksQuantityAfterScan());
    }

    @Test
    void previewLineTotalRoundsHalfUp_RF60() {
        assertEquals(new BigDecimal("29.93"),
                saleService.previewLineTotal(new BigDecimal("39.90"), new BigDecimal("0.750")));
    }

    @Test
    void previewLineTotalIsZeroForMissingOrNonPositiveInput_RF60() {
        assertEquals(new BigDecimal("0.00"), saleService.previewLineTotal(null, BigDecimal.ONE));
        assertEquals(new BigDecimal("0.00"), saleService.previewLineTotal(BigDecimal.ONE, BigDecimal.ZERO));
    }

    @Test
    void addingSameProductSumsQuantityAsBefore_RN07() {
        saleService.addItem(sale, unitCode, new BigDecimal("2"));
        Sale updated = saleService.addItem(sale, unitCode.toUpperCase(), new BigDecimal("3"));
        assertEquals(1, updated.getItems().size());
        assertEquals(0, new BigDecimal("5").compareTo(updated.getItems().get(0).getQuantity()));
    }
}
