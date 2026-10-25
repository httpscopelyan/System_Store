package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.ProductBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {

    List<ProductBatch> findByProduct_IdAndAvailableTrueOrderByExpiryDateAsc(Long productId);

    List<ProductBatch> findByAvailableTrueAndExpiryDateLessThanEqualOrderByExpiryDateAsc(LocalDate limit);
}
