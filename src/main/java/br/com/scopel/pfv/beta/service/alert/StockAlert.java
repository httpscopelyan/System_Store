package br.com.scopel.pfv.beta.service.alert;

import br.com.scopel.pfv.beta.model.Product;

import java.math.BigDecimal;

public record StockAlert(String code, String description, Product.Unidade unit, BigDecimal quantity,
                         BigDecimal minStock, BigDecimal suggestedQuantity, Severity severity) {

    public enum Severity {
        CRITICO,
        ATENCAO
    }

    public BigDecimal difference() {
        return quantity.subtract(minStock);
    }
}
