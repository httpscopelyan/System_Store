package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.Product;

import java.math.BigDecimal;

public record PendingScan(Product product) {

    public boolean isWeight() {
        return product.getUnit().soldByWeight();
    }

    public String quantityLabel() {
        return switch (product.getUnit()) {
            case UN -> "Qtd";
            case KG -> "Peso (KG)";
            case G100 -> "Peso (gramas)";
        };
    }

    public String quantityPrompt() {
        return switch (product.getUnit()) {
            case UN -> "Qtd";
            case KG -> "Ex: 0,750";
            case G100 -> "Ex: 250";
        };
    }

    public BigDecimal stockQuantity(BigDecimal typed) {
        return product.getUnit().stockQuantityFromTyped(typed);
    }

    public String code() {
        return product.getCode();
    }
}
