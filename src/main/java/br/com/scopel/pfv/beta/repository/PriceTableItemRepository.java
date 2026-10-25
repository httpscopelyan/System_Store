package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.PriceTableItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceTableItemRepository extends JpaRepository<PriceTableItem, Long> {

    Optional<PriceTableItem> findByPriceTable_IdAndProduct_Id(Long priceTableId, Long productId);

    List<PriceTableItem> findByPriceTable_Id(Long priceTableId);
}
