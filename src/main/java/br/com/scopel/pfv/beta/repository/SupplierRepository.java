package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByCnpj(String cnpj);

    List<Supplier> findByActiveTrue();

    List<Supplier> findByNameContainingIgnoreCase(String name);
}
