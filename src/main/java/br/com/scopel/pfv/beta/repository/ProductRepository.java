package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCodeIgnoreCase(String code);

    List<Product> findByCategory(String category);

    List<Product> findByDescriptionContainingIgnoreCase(String description);

    @Query("select p from Product p where p.active = true and p.minStock > 0 "
            + "and p.quantity <= p.minStock + :margin order by p.description")
    List<Product> findLowStock(@Param("margin") BigDecimal margin);

}
