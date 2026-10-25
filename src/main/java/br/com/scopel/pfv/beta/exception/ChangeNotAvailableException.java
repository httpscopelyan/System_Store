package br.com.scopel.pfv.beta.exception;

public class ChangeNotAvailableException extends RuntimeException {

    public ChangeNotAvailableException() {
        super("Troco só pode sair do valor recebido em dinheiro (RN18).");
    }
}
