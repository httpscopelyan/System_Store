package br.com.scopel.pfv.beta.exception;

public class CreditLimitExceededException extends RuntimeException {

    public CreditLimitExceededException() {
        super("Limite de crédito do cliente excedido.");
    }
}
