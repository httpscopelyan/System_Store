package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {


    Optional<CashRegister> findByOperatorAndStatus(User operator, CashRegister.Status status);


}
