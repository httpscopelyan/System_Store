package br.com.scopel.pfv.beta.exception;

public class InsufficientCashException extends RuntimeException {
    public InsufficientCashException() {
        super("Saldo Insuficiente para retirada!");
    }
}
