package br.com.scopel.pfv.beta.exception;

public class SaleItemNotFoundException extends RuntimeException {

    public SaleItemNotFoundException(String code) {
        super("Item não está na venda: " + code);
    }
}
