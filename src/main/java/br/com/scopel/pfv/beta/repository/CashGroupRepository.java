package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.CashGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashGroupRepository extends JpaRepository<CashGroup, Long> {

    List<CashGroup> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
