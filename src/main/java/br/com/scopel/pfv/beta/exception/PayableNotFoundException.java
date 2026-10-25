package br.com.scopel.pfv.beta.exception;

public class PayableNotFoundException extends RuntimeException {

    public PayableNotFoundException(Object ref) {
        super("Conta a pagar não encontrada: " + ref);
    }
}
