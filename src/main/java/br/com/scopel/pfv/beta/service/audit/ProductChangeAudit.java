package br.com.scopel.pfv.beta.service.audit;

import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public class ProductChangeAudit {

    private static final String ACTIVE_LABEL = "Ativo";
    private static final String INACTIVE_LABEL = "Inativo";

    private final AuditLogService auditLogService;

    public ProductChangeAudit(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public void recordCreation(User actor, Product product) {
        String createdValues = "Preço " + money(product.getPrice()) + " | Custo " + money(product.getCostPrice())
                + " | Estoque " + quantity(product.getQuantity());
        auditLogService.record(actor, AuditAction.PRODUCT_CREATED, AuditEntityType.PRODUCT, product.getId(),
                "Produto cadastrado: " + identify(product), null, createdValues);
    }

    public void recordChanges(User actor, ProductSnapshot before, Product after) {
        recordMoneyChange(actor, AuditAction.PRICE_CHANGE, "Preço de venda alterado", after, before.price(),
                after.getPrice());
        recordMoneyChange(actor, AuditAction.COST_CHANGE, "Custo alterado", after, before.costPrice(),
                after.getCostPrice());
        recordTextChange(actor, "Código alterado", after, before.code(), after.getCode());
        recordTextChange(actor, "Descrição alterada", after, before.description(), after.getDescription());
        recordTextChange(actor, "Unidade alterada", after, nameOf(before.unit()), nameOf(after.getUnit()));
        recordTextChange(actor, "Categoria alterada", after, before.category(), after.getCategory());
        recordTextChange(actor, "Situação alterada", after, activeLabel(before.active()),
                activeLabel(after.getActive()));
        if (differs(before.minStock(), after.getMinStock())) {
            auditLogService.record(actor, AuditAction.PRODUCT_UPDATED, AuditEntityType.PRODUCT, after.getId(),
                    "Estoque mínimo alterado: " + identify(after), quantity(before.minStock()),
                    quantity(after.getMinStock()));
        }
    }

    public void recordStockQuantity(User actor, AuditAction action, Product product, BigDecimal previousQuantity,
                                    String reason) {
        if (!differs(previousQuantity, product.getQuantity())) {
            return;
        }
        String reasonSuffix = reason == null || reason.isBlank() ? "" : " - motivo: " + reason.trim();
        auditLogService.record(actor, action, AuditEntityType.PRODUCT, product.getId(),
                stockLabel(action) + ": " + identify(product) + reasonSuffix, quantity(previousQuantity),
                quantity(product.getQuantity()));
    }

    private void recordMoneyChange(User actor, AuditAction action, String label, Product product,
                                   BigDecimal previousAmount, BigDecimal newAmount) {
        if (differs(previousAmount, newAmount)) {
            auditLogService.record(actor, action, AuditEntityType.PRODUCT, product.getId(),
                    label + ": " + identify(product), money(previousAmount), money(newAmount));
        }
    }

    private void recordTextChange(User actor, String label, Product product, String previousText, String newText) {
        if (!Objects.equals(previousText, newText)) {
            auditLogService.record(actor, AuditAction.PRODUCT_UPDATED, AuditEntityType.PRODUCT, product.getId(),
                    label + ": " + identify(product), previousText, newText);
        }
    }

    private String stockLabel(AuditAction action) {
        return switch (action) {
            case STOCK_ENTRY -> "Entrada de estoque";
            case STOCK_RECTIFICATION -> "Retificação de estoque";
            default -> "Ajuste de estoque";
        };
    }

    private boolean differs(BigDecimal previousNumber, BigDecimal newNumber) {
        if (previousNumber == null || newNumber == null) {
            return previousNumber != newNumber;
        }
        return previousNumber.compareTo(newNumber) != 0;
    }

    private String identify(Product product) {
        return product.getDescription() + " (cód. " + product.getCode() + ")";
    }

    private String money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String quantity(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.signum() == 0 ? "0" : amount.stripTrailingZeros().toPlainString();
    }

    private String nameOf(Product.Unidade unit) {
        return unit == null ? null : unit.name();
    }

    private String activeLabel(Boolean active) {
        return Boolean.TRUE.equals(active) ? ACTIVE_LABEL : INACTIVE_LABEL;
    }
}
