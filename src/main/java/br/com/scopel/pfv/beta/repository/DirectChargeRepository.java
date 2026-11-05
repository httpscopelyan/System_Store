package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.DirectCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DirectChargeRepository extends JpaRepository<DirectCharge, Long> {

    List<DirectCharge> findTop10ByOrderByCreatedAtDesc();
}
