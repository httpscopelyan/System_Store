package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.model.Product;

import java.math.BigDecimal;

public class SaleItemRow {
    private final String productCode;
    private final String description;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalPrice;
    private final Product.Unidade unit;

    public SaleItemRow(String productCode, String description, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal totalPrice, Product.Unidade unit) {
        this.productCode = productCode;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.unit = unit;
    }

    public String getProductCode() { return productCode; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public Product.Unidade getUnit() { return unit; }
}
