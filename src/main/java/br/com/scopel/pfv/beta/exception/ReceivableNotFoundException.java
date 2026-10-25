package br.com.scopel.pfv.beta.exception;

public class ReceivableNotFoundException extends RuntimeException {

    public ReceivableNotFoundException(Object ref) {
        super("Título a receber não encontrado: " + ref);
    }
}
