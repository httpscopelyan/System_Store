package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.PriceTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceTableRepository extends JpaRepository<PriceTable, Long> {

    Optional<PriceTable> findByName(String name);

    List<PriceTable> findByActiveTrue();
}
