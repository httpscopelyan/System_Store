package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.ProductBatch;
import br.com.scopel.pfv.beta.repository.ProductBatchRepository;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class ProductBatchService {

    private final ProductBatchRepository batchRepository;
    private final ProductRepository productRepository;

    public ProductBatchService(ProductBatchRepository batchRepository,
                               ProductRepository productRepository) {
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
    }

    public ProductBatch registerBatch(String code, String batchCode, LocalDate expiryDate,
                                      BigDecimal quantity) {
        Product product = productRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ProductNotfindException(code));

        ProductBatch batch = new ProductBatch();
        batch.setProduct(product);
        batch.setBatchCode(batchCode);
        batch.setExpiryDate(expiryDate);
        batch.setQuantityEntry(quantity.setScale(3, RoundingMode.HALF_UP));
        batch.setQuantitySold(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
        batch.setAvailable(true);
        return batchRepository.save(batch);
    }

    public List<ProductBatch> availableBatches(String code) {
        Product product = productRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ProductNotfindException(code));
        return batchRepository.findByProduct_IdAndAvailableTrueOrderByExpiryDateAsc(product.getId());
    }

    public List<ProductBatch> expiringUntil(LocalDate limit) {
        return batchRepository.findByAvailableTrueAndExpiryDateLessThanEqualOrderByExpiryDateAsc(limit);
    }
}
