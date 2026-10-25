package br.com.scopel.pfv.beta.exception;

public class CustomerCreditBlockedException extends RuntimeException {

    public CustomerCreditBlockedException() {
        super("Venda a prazo bloqueada para este cliente (bloqueio manual ou atraso).");
    }
}
