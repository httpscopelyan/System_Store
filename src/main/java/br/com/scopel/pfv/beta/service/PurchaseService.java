package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.IllegalPurchaseStateException;
import br.com.scopel.pfv.beta.exception.PurchaseNotFoundException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Purchase;
import br.com.scopel.pfv.beta.model.PurchaseItem;
import br.com.scopel.pfv.beta.model.Supplier;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.PurchaseRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Compras / entrada de mercadoria (PDV Prático: Compras + ItensCompra).
 * A compra é montada em estado ABERTA; ao RECEBER, dá entrada no estoque e
 * atualiza o custo de cada produto (via StockService), numa única transação.
 */
@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final ProductService productService;
    private final StockService stockService;

    public PurchaseService(PurchaseRepository purchaseRepository,
                           ProductService productService,
                           StockService stockService) {
        this.purchaseRepository = purchaseRepository;
        this.productService = productService;
        this.stockService = stockService;
    }

    public Purchase openPurchase(Supplier supplier, String invoiceNumber, User operator) {
        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setOperator(operator);
        purchase.setInvoiceNumber(invoiceNumber);
        purchase.setCreatedAt(LocalDateTime.now());
        purchase.setTotal(BigDecimal.ZERO);
        purchase.setStatus(Purchase.Status.ABERTA);
        return purchaseRepository.save(purchase);
    }

    public Purchase findById(Long id) {
        return purchaseRepository.findById(id).orElseThrow(() -> new PurchaseNotFoundException(id));
    }

    /** Adiciona item; repetir o mesmo produto soma a quantidade (como na venda, RN07). */
    public Purchase addItem(Purchase purchase, String productCode, BigDecimal quantity, BigDecimal unitCost) {
        if (purchase.getStatus() != Purchase.Status.ABERTA) {
            throw new IllegalPurchaseStateException();
        }

        Product product = productService.findCode(productCode);
        BigDecimal cost = unitCost.setScale(2, RoundingMode.HALF_UP);

        PurchaseItem existing = null;
        for (PurchaseItem item : purchase.getItems()) {
            if (item.getProduct().getId().equals(product.getId())) {
                existing = item;
                break;
            }
        }

        if (existing != null) {
            existing.setQuantity(existing.getQuantity().add(quantity));
            existing.setUnitCost(cost);
            existing.setTotalCost(cost.multiply(existing.getQuantity()).setScale(2, RoundingMode.HALF_UP));
        } else {
            PurchaseItem item = new PurchaseItem();
            item.setPurchase(purchase);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setUnitCost(cost);
            item.setTotalCost(cost.multiply(quantity).setScale(2, RoundingMode.HALF_UP));
            purchase.getItems().add(item);
        }

        purchase.setTotal(calculateTotal(purchase));
        return purchaseRepository.save(purchase);
    }

    /** Recebe a compra: entrada no estoque + atualização de custo, em transação única. */
    @Transactional
    public Purchase receive(Purchase purchase, User operator) {
        if (purchase.getStatus() != Purchase.Status.ABERTA || purchase.getItems().isEmpty()) {
            throw new IllegalPurchaseStateException();
        }

        for (PurchaseItem item : purchase.getItems()) {
            stockService.registerEntry(
                    item.getProduct().getCode(),
                    item.getQuantity(),
                    item.getUnitCost(),
                    "Compra #" + purchase.getId(),
                    operator);
        }

        purchase.setStatus(Purchase.Status.RECEBIDA);
        purchase.setReceivedAt(LocalDateTime.now());
        return purchaseRepository.save(purchase);
    }

    /** Cancela a compra enquanto não recebida (não desfaz estoque já lançado). */
    public Purchase cancel(Purchase purchase) {
        if (purchase.getStatus() == Purchase.Status.RECEBIDA) {
            throw new IllegalPurchaseStateException();
        }
        purchase.setStatus(Purchase.Status.CANCELADA);
        return purchaseRepository.save(purchase);
    }

    private BigDecimal calculateTotal(Purchase purchase) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseItem item : purchase.getItems()) {
            total = total.add(item.getTotalCost());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
