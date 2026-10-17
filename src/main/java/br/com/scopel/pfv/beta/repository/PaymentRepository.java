package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

}
