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
    private static final String VIEW_ONLY_MESSAGE = "Seu usuário só pode visualizar o estoque.";
    private static final String INLINE_EDIT_REASON = "Edição direta na tela de Estoque";

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final AppSettingService appSettingService;

    public StockService(ProductRepository productRepository,
                        StockMovementRepository movementRepository,
                        AppSettingService appSettingService) {
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.appSettingService = appSettingService;
    }

    public record CostPreview(BigDecimal currentCost, BigDecimal newCost, StockCostMode mode) {
    }

    public StockCostMode costMode() {
        return StockCostMode.fromSetting(appSettingService.getString(AppSettingService.STOCK_COST_MODE, null));
    }

    public CostPreview previewEntryCost(String code, BigDecimal quantity, BigDecimal unitCost) {
        Product product = productRepository.findByCodeIgnoreCase(code == null ? "" : code.trim())
                .orElseThrow(() -> new ProductNotfindException(code));
        return new CostPreview(product.getCostPrice(), costAfterEntry(product, quantity, unitCost), costMode());
    }

    private BigDecimal costAfterEntry(Product product, BigDecimal quantity, BigDecimal unitCost) {
        if (unitCost == null) {
            return product.getCostPrice();
        }
        BigDecimal entryCost = unitCost.setScale(2, RoundingMode.HALF_UP);
        if (costMode() == StockCostMode.MEDIA_PONDERADA && quantity != null) {
            return CostAveraging.weightedAverageCost(product.getQuantity(), product.getCostPrice(),
                    quantity.setScale(3, RoundingMode.HALF_UP), entryCost);
        }
        return entryCost;
    }

    @Transactional
    public Product registerEntry(String code, BigDecimal quantity, BigDecimal unitCost,
                                 String reason, User operator) {
        requireNotViewOnly(operator);
        Product product = productRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ProductNotfindException(code));

        BigDecimal qty = quantity.setScale(3, RoundingMode.HALF_UP);
        BigDecimal newCost = costAfterEntry(product, qty, unitCost);
        BigDecimal newStock = product.getQuantity().add(qty);
        product.setQuantity(newStock);
        if (unitCost != null) {
            product.setCostPrice(newCost);
        }
        productRepository.save(product);

        record(product, StockMovement.Type.ENTRADA, qty, unitCost, reason, operator, newStock);
        return product;
    }

    @Transactional
    public Product adjust(String code, BigDecimal newQuantity, String reason, User operator) {
        requireNotViewOnly(operator);
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
    public Product adjustQuantityInline(String code, BigDecimal newQuantity, User actor) {
        if (!StaffPermissions.canEditStockQuantity(actor)) {
            throw new ManagerAuthorizationRequiredException();
        }
        if (newQuantity == null || newQuantity.signum() < 0) {
            throw new IllegalArgumentException("A quantidade não pode ser negativa.");
        }
        return adjust(code, newQuantity, INLINE_EDIT_REASON, actor);
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

    private void requireNotViewOnly(User operator) {
        if (StaffPermissions.isViewOnly(operator)) {
            throw new IllegalArgumentException(VIEW_ONLY_MESSAGE);
        }
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
