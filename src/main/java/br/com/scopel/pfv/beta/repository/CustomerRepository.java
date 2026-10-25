package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCpfCnpj(String cpfCnpj);

    List<Customer> findByActiveTrue();

    List<Customer> findByNameContainingIgnoreCase(String name);
}
