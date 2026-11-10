package br.com.scopel.pfv.beta.service.audit;

import br.com.scopel.pfv.beta.model.Product;

import java.math.BigDecimal;

public record ProductSnapshot(String code, String description, Product.Unidade unit, BigDecimal price,
                              BigDecimal costPrice, BigDecimal minStock, String category, Boolean active) {

    public static ProductSnapshot of(Product product) {
        return new ProductSnapshot(product.getCode(), product.getDescription(), product.getUnit(),
                product.getPrice(), product.getCostPrice(), product.getMinStock(), product.getCategory(),
                product.getActive());
    }
}
