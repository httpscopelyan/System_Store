package br.com.scopel.pfv.beta.exception;

public class IllegalQuoteStateException extends RuntimeException {

    public IllegalQuoteStateException() {
        super("Operação inválida para o estado atual do orçamento.");
    }
}
