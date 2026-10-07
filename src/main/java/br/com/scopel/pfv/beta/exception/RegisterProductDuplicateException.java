package br.com.scopel.pfv.beta.exception;

public class RegisterProductDuplicateException extends RuntimeException{

    public RegisterProductDuplicateException(String code){
        super("Produto já existente: " + code);
    }
}
