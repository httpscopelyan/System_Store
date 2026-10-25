package br.com.scopel.pfv.beta.exception;

public class PriceTableNotFoundException extends RuntimeException {

    public PriceTableNotFoundException(Object ref) {
        super("Tabela de preço não encontrada: " + ref);
    }
}
