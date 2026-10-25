package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.IllegalPurchaseStateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Purchase;
import br.com.scopel.pfv.beta.model.Supplier;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.PurchaseService;
import br.com.scopel.pfv.beta.service.SupplierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PurchaseServiceTests {

    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private ProductService productService;

    private Supplier newSupplier() {
        Supplier s = new Supplier();
        s.setName("Forn " + UUID.randomUUID());
        return supplierService.save(s);
    }

    private Product newProduct(BigDecimal stock) {
        Product p = new Product();
        p.setCode("CMP" + UUID.randomUUID().toString().substring(0, 8));
        p.setDescription("Produto compra");
        p.setPrice(new BigDecimal("10.00"));
        p.setQuantity(stock);
        p.setUnit(Product.Unidade.UN);
        return productService.save(p);
    }

    @Test
    void addItemGroupsSameProductAndSumsTotal() {
        Purchase purchase = purchaseService.openPurchase(newSupplier(), "NF1", null);
        Product p = newProduct(new BigDecimal("0.000"));

        purchaseService.addItem(purchase, p.getCode(), new BigDecimal("4"), new BigDecimal("2.50"));
        purchase = purchaseService.addItem(purchase, p.getCode(), new BigDecimal("6"), new BigDecimal("2.50"));

        assertEquals(1, purchase.getItems().size());
        assertEquals(0, purchase.getItems().get(0).getQuantity().compareTo(new BigDecimal("10.000")));
        assertEquals(0, purchase.getTotal().compareTo(new BigDecimal("25.00")));
    }

    @Test
    void receiveAddsToStockAndUpdatesCost() {
        Purchase purchase = purchaseService.openPurchase(newSupplier(), "NF2", null);
        Product p = newProduct(new BigDecimal("5.000"));
        purchaseService.addItem(purchase, p.getCode(), new BigDecimal("10"), new BigDecimal("3.20"));

        Purchase received = purchaseService.receive(purchase, null);

        assertEquals(Purchase.Status.RECEBIDA, received.getStatus());
        assertNotNull(received.getReceivedAt());

        Product reloaded = productService.findCode(p.getCode());
        assertEquals(0, reloaded.getQuantity().compareTo(new BigDecimal("15.000")));
        assertEquals(0, reloaded.getCostPrice().compareTo(new BigDecimal("3.20")));
    }

    @Test
    void cannotAddItemAfterReceive() {
        Purchase purchase = purchaseService.openPurchase(newSupplier(), "NF3", null);
        Product p = newProduct(new BigDecimal("0.000"));
        purchaseService.addItem(purchase, p.getCode(), new BigDecimal("1"), new BigDecimal("1.00"));
        Purchase received = purchaseService.receive(purchase, null);

        assertThrows(IllegalPurchaseStateException.class,
                () -> purchaseService.addItem(received, p.getCode(), new BigDecimal("1"), new BigDecimal("1.00")));
    }

    @Test
    void cannotReceiveEmptyPurchase() {
        Purchase purchase = purchaseService.openPurchase(newSupplier(), "NF4", null);

        assertThrows(IllegalPurchaseStateException.class, () -> purchaseService.receive(purchase, null));
    }
}
