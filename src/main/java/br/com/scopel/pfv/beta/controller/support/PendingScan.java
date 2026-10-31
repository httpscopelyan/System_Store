package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.Product;

public record PendingScan(Product product) {

    public boolean isWeight() {
        return product.getUnit() == Product.Unidade.KG;
    }

    public String code() {
        return product.getCode();
    }
}
