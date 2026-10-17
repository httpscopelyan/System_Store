package br.com.scopel.pfv.beta.exception;

import br.com.scopel.pfv.beta.model.User;

public class CashRegisterAlreadyOpenException extends RuntimeException {
    public CashRegisterAlreadyOpenException(User operator) {
        super("Operador " + operator + "já possui caixa aberto!");
    }
}
