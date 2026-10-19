package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCode(String code);

    List<Product> findByCategory(String category);

    List<Product> findByDescriptionContainingIgnoreCase(String description);

}
