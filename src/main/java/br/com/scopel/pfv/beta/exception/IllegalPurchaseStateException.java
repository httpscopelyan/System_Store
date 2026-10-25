package br.com.scopel.pfv.beta.exception;

public class IllegalPurchaseStateException extends RuntimeException {

    public IllegalPurchaseStateException() {
        super("Operação inválida para o estado atual da compra.");
    }
}
