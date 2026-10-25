package br.com.scopel.pfv.beta.exception;

public class FinancialOperationException extends RuntimeException {

    public FinancialOperationException(String detail) {
        super(detail);
    }
}
