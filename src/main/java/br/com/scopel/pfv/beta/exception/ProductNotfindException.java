package br.com.scopel.pfv.beta.exception;

public class ProductNotfindException extends RuntimeException {

    public ProductNotfindException(String code) {
        super("Produto não encontrado: " + code );
    }

}
