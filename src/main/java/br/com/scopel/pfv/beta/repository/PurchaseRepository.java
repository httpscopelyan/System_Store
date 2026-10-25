package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findBySupplier_Id(Long supplierId);

    List<Purchase> findByStatus(Purchase.Status status);
}
