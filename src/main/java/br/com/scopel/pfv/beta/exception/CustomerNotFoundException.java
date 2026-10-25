package br.com.scopel.pfv.beta.exception;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Object ref) {
        super("Cliente não encontrado: " + ref);
    }
}
