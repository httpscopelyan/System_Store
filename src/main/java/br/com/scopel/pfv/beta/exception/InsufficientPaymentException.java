package br.com.scopel.pfv.beta.exception;

public class InsufficientPaymentException extends RuntimeException {
    public InsufficientPaymentException() {
        super("Valor recebido menor que o total da venda.");
    }
}
