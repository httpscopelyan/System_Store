package br.com.scopel.pfv.beta.exception;

public class SupplierDuplicatedException extends RuntimeException {

    public SupplierDuplicatedException(String cnpj) {
        super("Fornecedor já existente com este CNPJ: " + cnpj);
    }
}
