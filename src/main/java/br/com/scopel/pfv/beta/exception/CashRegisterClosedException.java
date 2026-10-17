package br.com.scopel.pfv.beta.exception;

public class CashRegisterClosedException extends RuntimeException {
    public CashRegisterClosedException() {
        super("Caixa Fechado!");
    }
}
