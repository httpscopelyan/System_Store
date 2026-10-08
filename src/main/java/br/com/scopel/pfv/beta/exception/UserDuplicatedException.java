package br.com.scopel.pfv.beta.exception;

public class UserDuplicatedException extends RuntimeException{

    public UserDuplicatedException() {
        super("Usuario Já Existente");
    }

}
