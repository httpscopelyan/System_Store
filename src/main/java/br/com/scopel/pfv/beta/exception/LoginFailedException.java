package br.com.scopel.pfv.beta.exception;

public class LoginFailedException extends RuntimeException {
    public LoginFailedException() {
        super("Senha ou login, inválidos!");
    }
}
