package br.com.scopel.pfv.beta.exception;

public class CardGatewayException extends RuntimeException {

    public CardGatewayException(String message) {
        super(message);
    }

    public CardGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
