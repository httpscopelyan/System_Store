package br.com.scopel.pfv.beta.exception;

public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(Object ref) {
        super("Orçamento não encontrado: " + ref);
    }
}
