package br.com.scopel.pfv.beta.service;


import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public Product findCode(String code) {
        return repository.findByCode(code).orElseThrow(() -> new ProductNotfindException(code));
    }

    public Product save(Product novo) {
        if (repository.findByCode(novo.getCode()).isPresent()) {
            throw new RegisterProductDuplicateException(novo.getCode());
        }
        return repository.save(novo);
    }

    public Product modificationPrice(String code, BigDecimal value) {
        Product p = repository.findByCode(code).orElseThrow(() -> new ProductNotfindException(code));

        p.setPrice(value);

        return repository.save(p);
    }
}
