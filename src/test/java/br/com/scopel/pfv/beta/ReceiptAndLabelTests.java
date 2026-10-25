package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.LabelService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste puro (sem Spring): a formatação não depende de banco. AppSettingService
 * é substituído por um stub que devolve sempre o default.
 */
class ReceiptAndLabelTests {

    private final PrinterPort noopPrinter = new PrinterPort() {
        @Override public void print(String content) { }
        @Override public void openDrawer() { }
    };

    private final AppSettingService settingsStub = new AppSettingService(null) {
        @Override public String getString(String key, String defaultValue) {
            return defaultValue;
        }
    };

    private Product product(String desc, String code, String price) {
        Product p = new Product();
        p.setDescription(desc);
        p.setCode(code);
        p.setPrice(new BigDecimal(price));
        return p;
    }

    private Sale saleWithOneItem() {
        Product p = product("Arroz 5kg", "7891234567890", "25.50");
        SaleItem item = new SaleItem();
        item.setProduct(p);
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("25.50"));
        item.setDiscount(BigDecimal.ZERO);
        item.setTotalPrice(new BigDecimal("51.00"));

        Sale sale = new Sale();
        sale.setCreatedAt(LocalDateTime.now());
        sale.setDiscount(BigDecimal.ZERO);
        sale.setSurcharge(BigDecimal.ZERO);
        sale.setTotal(new BigDecimal("51.00"));
        sale.getItems().add(item);
        return sale;
    }

    @Test
    void receiptHasNonFiscalWarningAndItems() {
        ReceiptService rs = new ReceiptService(settingsStub, noopPrinter);

        String receipt = rs.buildSaleReceipt(saleWithOneItem());

        assertTrue(receipt.contains("NAO E DOCUMENTO FISCAL"), "aviso RNF15");
        assertTrue(receipt.contains("Arroz 5kg"), "item");
        assertTrue(receipt.contains("TOTAL"), "linha de total");
        assertTrue(receipt.contains("51,00"), "valor total");
    }

    @Test
    void labelHasDescriptionCodeAndPrice() {
        LabelService ls = new LabelService(noopPrinter);

        String label = ls.buildLabel(product("Cafe 500g", "7890000000011", "12.90"));

        assertTrue(label.contains("Cafe 500g"));
        assertTrue(label.contains("*7890000000011*"));
        assertTrue(label.contains("12,90"));
    }
}
