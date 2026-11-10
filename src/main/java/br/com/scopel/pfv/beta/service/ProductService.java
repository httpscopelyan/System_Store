package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.audit.ProductChangeAudit;
import br.com.scopel.pfv.beta.service.audit.ProductSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ProductService {

    private static final int CODE_MAX_LENGTH = 30;
    private static final int DESCRIPTION_MAX_LENGTH = 100;
    private static final Pattern VALID_CODE = Pattern.compile("[\\p{L}\\p{N} .\\-]+");

    private final ProductRepository repository;
    private final ProductChangeAudit productChangeAudit;

    public ProductService(ProductRepository repository, ProductChangeAudit productChangeAudit) {
        this.repository = repository;
        this.productChangeAudit = productChangeAudit;
    }

    public Product findCode(String code) {
        String normalized = code == null ? "" : code.trim();
        return repository.findByCodeIgnoreCase(normalized)
                .orElseThrow(() -> new ProductNotfindException(normalized));
    }

    public List<Product> searchForSale(String term, int limit) {
        String normalized = term == null ? "" : term.trim();
        if (normalized.isEmpty() || limit <= 0) {
            return List.of();
        }
        Map<Long, Product> results = new LinkedHashMap<>();
        repository.findByCodeIgnoreCase(normalized).filter(Product::getActive)
                .ifPresent(exact -> results.put(exact.getId(), exact));
        if (normalized.chars().allMatch(Character::isDigit)) {
            addAll(results, repository.findTop20ByActiveTrueAndCodeStartingWithIgnoreCaseOrderByCode(normalized));
        } else {
            addAll(results, repository.findTop20ByActiveTrueAndCodeStartingWithIgnoreCaseOrderByCode(normalized));
            addAll(results, repository
                    .findTop20ByActiveTrueAndDescriptionStartingWithIgnoreCaseOrderByDescription(normalized));
            addAll(results, repository
                    .findTop20ByActiveTrueAndDescriptionContainingIgnoreCaseOrderByDescription(normalized));
        }
        return results.values().stream().limit(limit).toList();
    }

    public String resolveCode(String text) {
        String normalized = text == null ? "" : text.trim();
        if (repository.findByCodeIgnoreCase(normalized).isPresent()) {
            return repository.findByCodeIgnoreCase(normalized).get().getCode();
        }
        List<Product> matches = searchForSale(normalized, 2);
        if (matches.isEmpty()) {
            throw new ProductNotfindException(normalized);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "Mais de um produto com esse nome. Escolha na lista ou digite o código.");
        }
        return matches.get(0).getCode();
    }

    private void addAll(Map<Long, Product> results, List<Product> products) {
        for (Product product : products) {
            results.putIfAbsent(product.getId(), product);
        }
    }

    public Product findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotfindException("id " + id));
    }

    @Transactional
    public Product create(String code, String description, Product.Unidade unit, BigDecimal price,
                          BigDecimal costPrice, BigDecimal quantity, BigDecimal minStock,
                          String category, boolean active) {
        return create(code, description, unit, price, costPrice, quantity, minStock, category, active, null);
    }

    @Transactional
    public Product create(String code, String description, Product.Unidade unit, BigDecimal price,
                          BigDecimal costPrice, BigDecimal quantity, BigDecimal minStock,
                          String category, boolean active, User actor) {
        requireProductRegistrationAllowed(actor);
        String normalizedCode = normalizeCode(code);
        requireDescription(description);
        requireUnit(unit);
        requireNonNegative(price, "O preço");
        requireNonNegative(costPrice, "O custo");
        requireNonNegative(quantity, "A quantidade");
        requireNonNegative(minStock, "O estoque mínimo");

        Product product = new Product();
        product.setCode(normalizedCode);
        product.setDescription(description.trim());
        product.setUnit(unit);
        product.setPrice(price.setScale(2, RoundingMode.HALF_UP));
        product.setCostPrice(costPrice.setScale(2, RoundingMode.HALF_UP));
        product.setQuantity(quantity.setScale(3, RoundingMode.HALF_UP));
        product.setMinStock(minStock.setScale(3, RoundingMode.HALF_UP));
        product.setCategory(normalizeCategory(category));
        product.setActive(active);
        return save(product, actor);
    }

    @Transactional
    public Product changeCode(Long id, String newCode) {
        return changeCode(id, newCode, null);
    }

    @Transactional
    public Product changeCode(Long id, String newCode, User actor) {
        requireProductDataEditor(actor);
        Product product = findById(id);
        ProductSnapshot beforeChange = ProductSnapshot.of(product);
        String normalized = normalizeCode(newCode);
        if (normalized.equals(product.getCode())) {
            return product;
        }
        boolean takenByAnother = repository.findByCodeIgnoreCase(normalized)
                .filter(other -> !other.getId().equals(product.getId()))
                .isPresent();
        if (takenByAnother) {
            throw new RegisterProductDuplicateException(normalized);
        }
        product.setCode(normalized);
        Product renamed = repository.save(product);
        productChangeAudit.recordChanges(actor, beforeChange, renamed);
        return renamed;
    }

    @Transactional
    public Product update(Long id, String code, String description, Product.Unidade unit, BigDecimal price,
                          BigDecimal costPrice, BigDecimal minStock, String category, boolean active) {
        return update(id, code, description, unit, price, costPrice, minStock, category, active, null);
    }

    @Transactional
    public Product update(Long id, String code, String description, Product.Unidade unit, BigDecimal price,
                          BigDecimal costPrice, BigDecimal minStock, String category, boolean active,
                          User actor) {
        changeCode(id, code, actor);
        return update(id, description, unit, price, costPrice, minStock, category, active, actor);
    }

    @Transactional
    public Product update(Long id, String description, Product.Unidade unit, BigDecimal price,
                          BigDecimal costPrice, BigDecimal minStock, String category, boolean active) {
        return update(id, description, unit, price, costPrice, minStock, category, active, null);
    }

    @Transactional
    public Product update(Long id, String description, Product.Unidade unit, BigDecimal price,
                          BigDecimal costPrice, BigDecimal minStock, String category, boolean active,
                          User actor) {
        requireProductDataEditor(actor);
        Product product = findById(id);
        ProductSnapshot beforeChange = ProductSnapshot.of(product);

        requireDescription(description);
        requireUnit(unit);
        requireNonNegative(price, "O preço");
        requireNonNegative(costPrice, "O custo");
        requireNonNegative(minStock, "O estoque mínimo");

        product.setDescription(description.trim());
        product.setUnit(unit);
        product.setPrice(price.setScale(2, RoundingMode.HALF_UP));
        product.setCostPrice(costPrice.setScale(2, RoundingMode.HALF_UP));
        product.setMinStock(minStock.setScale(3, RoundingMode.HALF_UP));
        product.setCategory(normalizeCategory(category));
        product.setActive(active);
        Product updated = repository.save(product);
        productChangeAudit.recordChanges(actor, beforeChange, updated);
        return updated;
    }

    public List<Product> listAll() {
        return repository.findAll();
    }

    public List<Product> searchByName(String name) {
        if (name == null || name.isBlank()) {
            return repository.findAll();
        }
        return repository.findByDescriptionContainingIgnoreCase(name.trim());
    }

    public List<Product> filterForStock(String code, String name, boolean belowMinimumOnly,
                                        boolean withoutCostOnly) {
        return filterForStock(code, name, belowMinimumOnly, withoutCostOnly, false);
    }

    public List<Product> filterForStock(String code, String name, boolean belowMinimumOnly,
                                        boolean withoutCostOnly, boolean inactiveOnly) {
        List<Product> base = code != null && !code.isBlank()
                ? repository.findByCodeIgnoreCase(code.trim()).map(List::of).orElse(List.of())
                : searchByName(name);
        return base.stream()
                .filter(product -> inactiveOnly != Boolean.TRUE.equals(product.getActive()))
                .filter(product -> !belowMinimumOnly || isAtOrBelowMinimum(product))
                .filter(product -> !withoutCostOnly || hasNoCost(product))
                .sorted(Comparator.comparing(Product::getDescription, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean isAtOrBelowMinimum(Product product) {
        return product.getMinStock().signum() > 0 && product.getQuantity().compareTo(product.getMinStock()) <= 0;
    }

    private boolean hasNoCost(Product product) {
        return product.getCostPrice() == null || product.getCostPrice().signum() <= 0;
    }

    @Transactional
    public Product inactivate(String code, User actor) {
        if (actor == null || actor.getRole() != User.Role.GERENTE) {
            throw new ManagerAuthorizationRequiredException();
        }
        Product product = findCode(code);
        ProductSnapshot beforeChange = ProductSnapshot.of(product);
        product.setActive(false);
        Product inactivated = repository.save(product);
        productChangeAudit.recordChanges(actor, beforeChange, inactivated);
        return inactivated;
    }

    @Transactional
    public Product save(Product product) {
        return save(product, null);
    }

    @Transactional
    public Product save(Product product, User actor) {
        requireProductRegistrationAllowed(actor);
        if (repository.findByCodeIgnoreCase(product.getCode()).isPresent()) {
            throw new RegisterProductDuplicateException(product.getCode());
        }
        Product registered = repository.save(product);
        productChangeAudit.recordCreation(actor, registered);
        return registered;
    }

    @Transactional
    public Product modificationPrice(String code, BigDecimal value) {
        return modificationPrice(code, value, null);
    }

    @Transactional
    public Product modificationPrice(String code, BigDecimal value, User actor) {
        requirePriceEditor(actor);
        Product product = findCode(code);
        ProductSnapshot beforeChange = ProductSnapshot.of(product);
        product.setPrice(value);
        Product repriced = repository.save(product);
        productChangeAudit.recordChanges(actor, beforeChange, repriced);
        return repriced;
    }

    @Transactional
    public int adjustPricesByPercent(BigDecimal percent, String category) {
        return adjustPricesByPercent(percent, category, null);
    }

    @Transactional
    public int adjustPricesByPercent(BigDecimal percent, String category, User actor) {
        requirePriceEditor(actor);
        List<Product> products = (category == null || category.isBlank())
                ? repository.findAll()
                : repository.findByCategory(category);

        BigDecimal factor = BigDecimal.ONE.add(percent.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        int changed = 0;
        for (Product product : products) {
            ProductSnapshot beforeChange = ProductSnapshot.of(product);
            BigDecimal adjusted = product.getPrice().multiply(factor).setScale(2, RoundingMode.HALF_UP);
            if (adjusted.compareTo(BigDecimal.ZERO) < 0) {
                adjusted = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            product.setPrice(adjusted);
            repository.save(product);
            productChangeAudit.recordChanges(actor, beforeChange, product);
            changed++;
        }
        return changed;
    }

    private void requireProductRegistrationAllowed(User actor) {
        if (actor != null && !StaffPermissions.canRegisterProducts(actor)) {
            throw new ManagerAuthorizationRequiredException();
        }
    }

    private void requireProductDataEditor(User actor) {
        if (actor != null && !StaffPermissions.canEditProductData(actor)) {
            throw new ManagerAuthorizationRequiredException();
        }
    }

    private void requirePriceEditor(User actor) {
        if (actor != null && !StaffPermissions.canEditPrices(actor)) {
            throw new ManagerAuthorizationRequiredException();
        }
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("O código é obrigatório.");
        }
        if (normalized.length() > CODE_MAX_LENGTH) {
            throw new IllegalArgumentException("O código aceita no máximo " + CODE_MAX_LENGTH + " caracteres.");
        }
        if (!VALID_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Código inválido: use letras, números, espaço, - ou .");
        }
        return normalized;
    }

    private void requireDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A descrição é obrigatória.");
        }
        if (description.trim().length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("A descrição aceita no máximo " + DESCRIPTION_MAX_LENGTH + " caracteres.");
        }
    }

    private void requireUnit(Product.Unidade unit) {
        if (unit == null) {
            throw new IllegalArgumentException("Selecione a unidade.");
        }
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? null : category.trim();
    }

    private void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo.");
        }
    }
}
