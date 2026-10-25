package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.SupplierDuplicatedException;
import br.com.scopel.pfv.beta.exception.SupplierNotFoundException;
import br.com.scopel.pfv.beta.model.Supplier;
import br.com.scopel.pfv.beta.repository.SupplierRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    public Supplier save(Supplier supplier) {
        supplier.setCnpj(normalizeDocument(supplier.getCnpj()));
        if (supplier.getCnpj() != null
                && repository.findByCnpj(supplier.getCnpj()).isPresent()) {
            throw new SupplierDuplicatedException(supplier.getCnpj());
        }
        return repository.save(supplier);
    }

    public Supplier findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new SupplierNotFoundException(id));
    }

    public List<Supplier> listActive() {
        return repository.findByActiveTrue();
    }

    public List<Supplier> searchByName(String name) {
        return repository.findByNameContainingIgnoreCase(name);
    }

    private String normalizeDocument(String doc) {
        if (doc == null) {
            return null;
        }
        String digits = doc.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }
}
