package br.com.scopel.pfv.beta.exception;

public class CustomerDuplicatedException extends RuntimeException {

    public CustomerDuplicatedException(String cpfCnpj) {
        super("Cliente já existente com este CPF/CNPJ: " + cpfCnpj);
    }
}
