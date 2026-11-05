package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

@Service
public class ProductBulkEditService {

    public record BulkResult(int changed, int skipped) {
    }

    private final ProductRepository repository;
    private final PricingSuggestionService pricingSuggestionService;
    private final StockService stockService;
    private final ProductService productService;

    public ProductBulkEditService(ProductRepository repository, PricingSuggestionService pricingSuggestionService,
                                  StockService stockService, ProductService productService) {
        this.productService = productService;
        this.repository = repository;
        this.pricingSuggestionService = pricingSuggestionService;
        this.stockService = stockService;
    }

    @Transactional
    public BulkResult applyPrice(Collection<Long> productIds, BigDecimal price, User actor) {
        requireManager(actor);
        requireNonNegative(price, "O preço");
        BigDecimal normalized = price.setScale(2, RoundingMode.HALF_UP);
        return update(productIds, product -> {
            product.setPrice(normalized);
            return true;
        });
    }

    @Transactional
    public BulkResult applyCost(Collection<Long> productIds, BigDecimal cost, User actor) {
        requireManager(actor);
        requireNonNegative(cost, "O custo");
        BigDecimal normalized = cost.setScale(2, RoundingMode.HALF_UP);
        return update(productIds, product -> {
            product.setCostPrice(normalized);
            return true;
        });
    }

    @Transactional
    public BulkResult applyMarginPercent(Collection<Long> productIds, BigDecimal marginPercent, User actor) {
        requireManager(actor);
        requireNonNegative(marginPercent, "A margem");
        return update(productIds, product -> {
            BigDecimal price = pricingSuggestionService.suggestFor(product.getCostPrice(), marginPercent);
            if (price == null) {
                return false;
            }
            product.setPrice(price);
            return true;
        });
    }

    @Transactional
    public BulkResult applySuggestedPrice(Collection<Long> productIds, User actor) {
        requireManager(actor);
        return update(productIds, product -> {
            BigDecimal price = pricingSuggestionService.suggestFor(product);
            if (price == null) {
                return false;
            }
            product.setPrice(price);
            return true;
        });
    }

    @Transactional
    public BulkResult applyCode(Long productId, String code) {
        productService.changeCode(productId, code);
        return new BulkResult(1, 0);
    }

    @Transactional
    public BulkResult applyStockQuantity(Collection<Long> productIds, BigDecimal quantity, User actor) {
        if (!StaffPermissions.canEditStockQuantity(actor)) {
            throw new ManagerAuthorizationRequiredException();
        }
        List<Product> products = repository.findAllById(productIds);
        for (Product product : products) {
            stockService.adjustQuantityInline(product.getCode(), quantity, actor);
        }
        return new BulkResult(products.size(), 0);
    }

    @Transactional
    public BulkResult applyMinStock(Collection<Long> productIds, BigDecimal minStock) {
        requireNonNegative(minStock, "O estoque mínimo");
        BigDecimal normalized = minStock.setScale(3, RoundingMode.HALF_UP);
        return update(productIds, product -> {
            product.setMinStock(normalized);
            return true;
        });
    }

    @Transactional
    public BulkResult applyDescription(Collection<Long> productIds, String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A descrição é obrigatória.");
        }
        String normalized = description.trim();
        return update(productIds, product -> {
            product.setDescription(normalized);
            return true;
        });
    }

    private BulkResult update(Collection<Long> productIds, Predicate<Product> change) {
        List<Product> products = repository.findAllById(productIds);
        int changed = 0;
        int skipped = 0;
        for (Product product : products) {
            if (change.test(product)) {
                repository.save(product);
                changed++;
            } else {
                skipped++;
            }
        }
        return new BulkResult(changed, skipped);
    }

    private void requireManager(User actor) {
        if (actor == null || actor.getRole() != User.Role.GERENTE) {
            throw new ManagerAuthorizationRequiredException();
        }
    }

    private void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo.");
        }
    }
}
