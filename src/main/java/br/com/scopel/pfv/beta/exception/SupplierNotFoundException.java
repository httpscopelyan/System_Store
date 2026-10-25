package br.com.scopel.pfv.beta.exception;

public class SupplierNotFoundException extends RuntimeException {

    public SupplierNotFoundException(Object ref) {
        super("Fornecedor não encontrado: " + ref);
    }
}
