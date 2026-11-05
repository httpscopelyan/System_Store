package br.com.scopel.pfv.beta.controller;

import java.math.BigDecimal;

public class SaleItemRow {
    private final String productCode;
    private final String description;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalPrice;

    public SaleItemRow(String productCode, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal totalPrice) {
        this.productCode = productCode;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
    }

    public String getProductCode() { return productCode; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalPrice() { return totalPrice; }
}