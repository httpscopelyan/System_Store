package br.com.scopel.pfv.beta.exception;

public class UserNotFindException extends RuntimeException{
    public UserNotFindException() {
        super("Usuario não encontrado");
    }
}
