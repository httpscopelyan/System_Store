package br.com.scopel.pfv.beta.exception;

public class InvalidDiscountException extends RuntimeException {

    public InvalidDiscountException(String detail) {
        super("Desconto inválido: " + detail);
    }
}
