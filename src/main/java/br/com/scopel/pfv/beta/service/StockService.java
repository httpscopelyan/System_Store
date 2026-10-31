package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.StockMovement;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.StockMovementRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StockService {

    private static final int MAX_REASON_LENGTH = 120;

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;

    public StockService(ProductRepository productRepository,
                        StockMovementRepository movementRepository) {
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public Product registerEntry(String code, BigDecimal quantity, BigDecimal unitCost,
                                 String reason, User operator) {
        Product product = productRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ProductNotfindException(code));

        BigDecimal qty = quantity.setScale(3, RoundingMode.HALF_UP);
        BigDecimal newStock = product.getQuantity().add(qty);
        product.setQuantity(newStock);
        if (unitCost != null) {
            product.setCostPrice(unitCost.setScale(2, RoundingMode.HALF_UP));
        }
        productRepository.save(product);

        record(product, StockMovement.Type.ENTRADA, qty, unitCost, reason, operator, newStock);
        return product;
    }

    @Transactional
    public Product adjust(String code, BigDecimal newQuantity, String reason, User operator) {
        String normalizedReason = MovementReason.require(reason, MAX_REASON_LENGTH);
        Product product = productRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ProductNotfindException(code));

        BigDecimal target = newQuantity.setScale(3, RoundingMode.HALF_UP);
        BigDecimal delta = target.subtract(product.getQuantity()).abs();
        product.setQuantity(target);
        productRepository.save(product);

        record(product, StockMovement.Type.AJUSTE, delta, null, normalizedReason, operator, target);
        return product;
    }

    @Transactional
    public Product rectify(String code, BigDecimal correctQuantity, String reason, User authorizedBy) {
        if (authorizedBy == null || authorizedBy.getRole() != User.Role.GERENTE) {
            throw new ManagerAuthorizationRequiredException();
        }
        String normalizedReason = MovementReason.require(reason, MAX_REASON_LENGTH);
        if (correctQuantity == null || correctQuantity.signum() < 0) {
            throw new IllegalArgumentException("A quantidade corrigida não pode ser negativa.");
        }
        Product product = productRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ProductNotfindException(code));
        BigDecimal target = correctQuantity.setScale(3, RoundingMode.HALF_UP);
        BigDecimal delta = target.subtract(product.getQuantity()).abs();
        StockMovement lastMovement = movementRepository.findByProduct_IdOrderByCreatedAtDesc(product.getId())
                .stream().findFirst().orElse(null);
        product.setQuantity(target);
        productRepository.save(product);

        StockMovement rectification = record(product, StockMovement.Type.RETIFICACAO, delta, null,
                normalizedReason, authorizedBy, target);
        rectification.setCorrectedMovement(lastMovement);
        rectification.setAuthorizedBy(authorizedBy);
        movementRepository.save(rectification);
        return product;
    }

    public List<StockMovement> history(String code) {
        Product product = productRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ProductNotfindException(code));
        return movementRepository.findByProduct_IdOrderByCreatedAtDesc(product.getId());
    }

    private StockMovement record(Product product, StockMovement.Type type, BigDecimal quantity,
                        BigDecimal unitCost, String reason, User operator, BigDecimal resultingStock) {
        StockMovement m = new StockMovement();
        m.setProduct(product);
        m.setType(type);
        m.setQuantity(quantity);
        m.setUnitCost(unitCost == null ? null : unitCost.setScale(2, RoundingMode.HALF_UP));
        m.setReason(reason);
        m.setOperator(operator);
        m.setCreatedAt(LocalDateTime.now());
        m.setResultingStock(resultingStock);
        return movementRepository.save(m);
    }
}
