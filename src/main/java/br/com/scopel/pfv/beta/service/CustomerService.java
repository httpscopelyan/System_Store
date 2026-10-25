package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.CustomerDuplicatedException;
import br.com.scopel.pfv.beta.exception.CustomerNotFoundException;
import br.com.scopel.pfv.beta.model.Customer;
import br.com.scopel.pfv.beta.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public Customer save(Customer customer) {
        customer.setCpfCnpj(normalizeDocument(customer.getCpfCnpj()));
        if (customer.getCpfCnpj() != null
                && repository.findByCpfCnpj(customer.getCpfCnpj()).isPresent()) {
            throw new CustomerDuplicatedException(customer.getCpfCnpj());
        }
        if (customer.getCreatedAt() == null) {
            customer.setCreatedAt(LocalDateTime.now());
        }
        return repository.save(customer);
    }

    public Customer findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    public List<Customer> listActive() {
        return repository.findByActiveTrue();
    }

    public List<Customer> searchByName(String name) {
        return repository.findByNameContainingIgnoreCase(name);
    }

    /** Bloqueia/desbloqueia venda a prazo para o cliente (ex.: em atraso). */
    public Customer setCreditBlocked(Long id, boolean blocked) {
        Customer c = findById(id);
        c.setCreditBlocked(blocked);
        return repository.save(c);
    }

    // CPF/CNPJ é texto; guardamos só os dígitos. Vazio vira nulo.
    private String normalizeDocument(String doc) {
        if (doc == null) {
            return null;
        }
        String digits = doc.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }
}
