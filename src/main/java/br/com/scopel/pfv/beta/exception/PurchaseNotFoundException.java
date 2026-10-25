package br.com.scopel.pfv.beta.exception;

public class PurchaseNotFoundException extends RuntimeException {

    public PurchaseNotFoundException(Object ref) {
        super("Compra não encontrada: " + ref);
    }
}
