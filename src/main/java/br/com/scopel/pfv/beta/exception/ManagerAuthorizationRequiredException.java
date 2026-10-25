package br.com.scopel.pfv.beta.exception;

public class ManagerAuthorizationRequiredException extends RuntimeException {

    public ManagerAuthorizationRequiredException() {
        super("Operação exige autorização de um gerente.");
    }
}
