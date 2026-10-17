package br.com.scopel.pfv.beta.exception;

public class IllegalSaleStateException extends RuntimeException {
    public IllegalSaleStateException() {
        super("Operação não permitida no estado atual da venda.");
    }
}
