package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SaleFinalizer {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;

    public SaleFinalizer(SaleRepository saleRepository, ProductRepository productRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Sale finalizeSale(Sale sale) {
        if (sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }
        decreaseStock(sale);
        sale.setStatus(Sale.Status.FINALIZADA);
        return saleRepository.save(sale);
    }

    private void decreaseStock(Sale sale) {
        for (SaleItem item : sale.getItems()) {
            Product product = item.getProduct();
            product.setQuantity(product.getQuantity().subtract(item.getQuantity()));
            productRepository.save(product);
        }
    }
}
