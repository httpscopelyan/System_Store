package br.com.scopel.pfv.beta.service;


import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public Product findCode(String code) {
        return repository.findByCode(code).orElseThrow(() -> new ProductNotfindException(code));
    }

    public List<Product> listAll() {
        return repository.findAll();
    }

    /** Busca por nome (parcial) para o filtro do Estoque (RF05). */
    public List<Product> searchByName(String name) {
        if (name == null || name.isBlank()) {
            return repository.findAll();
        }
        return repository.findByDescriptionContainingIgnoreCase(name.trim());
    }

    /** Inativa o produto (RF03 — nunca excluir de fato). */
    public Product inactivate(String code) {
        Product p = findCode(code);
        p.setActive(false);
        return repository.save(p);
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

    /**
     * Ajuste de preços em massa (PDV Prático: AjusteDePrecos). Aplica um percentual
     * (ex.: +10 = aumenta 10%, -5 = reduz 5%) ao preço de venda. Se category for
     * informado, ajusta só a categoria; senão, todos. Não altera vendas passadas
     * (RN19: o preço do item já está congelado). Retorna quantos produtos mudaram.
     */
    public int adjustPricesByPercent(BigDecimal percent, String category) {
        List<Product> products = (category == null || category.isBlank())
                ? repository.findAll()
                : repository.findByCategory(category);

        BigDecimal factor = BigDecimal.ONE.add(percent.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        int changed = 0;
        for (Product p : products) {
            BigDecimal novo = p.getPrice().multiply(factor).setScale(2, RoundingMode.HALF_UP);
            if (novo.compareTo(BigDecimal.ZERO) < 0) {
                novo = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            p.setPrice(novo);
            repository.save(p);
            changed++;
        }
        return changed;
    }
}
