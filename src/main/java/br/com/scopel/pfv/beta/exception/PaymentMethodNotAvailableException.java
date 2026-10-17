package br.com.scopel.pfv.beta.exception;

public class PaymentMethodNotAvailableException extends RuntimeException {
    public PaymentMethodNotAvailableException() {
        super("Forma de pagamento indisponível.");
    }
}
