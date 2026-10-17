package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    List<CashMovement> findByCashRegister(CashRegister cashRegister);

}
