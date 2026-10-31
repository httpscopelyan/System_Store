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
class SaleStockLimitTests {

    @Autowired private SaleService saleService;
    @Autowired private ProductService productService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private UserRepository userRepository;
    @Autowired private AppSettingService appSettingService;

    private Sale openSale() {
        User operator = new User();
        operator.setName("Operador");
        operator.setLogin("op_" + UUID.randomUUID());
        operator.setPassword("x");
        operator.setRole(User.Role.OPERADOR);
        operator.setActive(true);
        operator = userRepository.save(operator);
        CashRegister cashRegister = cashRegisterService.open(operator, new BigDecimal("100.00"));
        return saleService.openSale(operator, cashRegister);
    }

    private String product(String stock, Product.Unidade unit) {
        String code = "stk" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        productService.create(code, "Produto " + code, unit, new BigDecimal("5.00"), BigDecimal.ONE,
                new BigDecimal(stock), BigDecimal.ZERO, null, true);
        return code;
    }

    @Test
    void quantityAboveStockIsRefusedWithWarning_RN32() {
        Sale sale = openSale();
        String code = product("3", Product.Unidade.UN);
        InvalidQuantityException refused = assertThrows(InvalidQuantityException.class,
                () -> saleService.addItem(sale, code, new BigDecimal("4")));
        assertTrue(refused.getMessage().startsWith("Estoque insuficiente"));
        assertTrue(sale.getItems().isEmpty());
    }

    @Test
    void quantityEqualToStockIsAccepted_RN32() {
        Sale sale = openSale();
        String code = product("3", Product.Unidade.UN);
        Sale updated = saleService.addItem(sale, code, new BigDecimal("3"));
        assertEquals(0, new BigDecimal("3").compareTo(updated.getItems().get(0).getQuantity()));
    }

    @Test
    void repeatedScansCountWhatIsAlreadyInTheSale_RN32() {
        Sale sale = openSale();
        String code = product("3", Product.Unidade.UN);
        Sale updated = saleService.addItem(sale, code, new BigDecimal("2"));
        assertThrows(InvalidQuantityException.class, () -> saleService.addItem(updated, code, new BigDecimal("2")));
        assertEquals(0, new BigDecimal("2").compareTo(updated.getItems().get(0).getQuantity()));
    }

    @Test
    void weightProductIsLimitedByStockInKg_RN32() {
        Sale sale = openSale();
        String code = product("1.500", Product.Unidade.KG);
        assertThrows(InvalidQuantityException.class, () -> saleService.addItem(sale, code, new BigDecimal("1.501")));
        Sale updated = saleService.addItem(sale, code, new BigDecimal("1.500"));
        assertEquals(1, updated.getItems().size());
    }

    @Test
    void negativeStockSettingLiftsTheLimit_RN32() {
        appSettingService.set(AppSettingService.SALE_ALLOW_NEGATIVE_STOCK, "true");
        Sale sale = openSale();
        String code = product("1", Product.Unidade.UN);
        Sale updated = saleService.addItem(sale, code, new BigDecimal("5"));
        assertEquals(0, new BigDecimal("5").compareTo(updated.getItems().get(0).getQuantity()));
    }
}
