package br.com.scopel.pfv.beta.exception;

public class MissingReasonException extends RuntimeException {

    public MissingReasonException(String message) {
        super(message);
    }
}
