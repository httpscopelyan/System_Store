package br.com.scopel.pfv.beta.exception;

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException() {
        super("Senha incorreta");
    }
}