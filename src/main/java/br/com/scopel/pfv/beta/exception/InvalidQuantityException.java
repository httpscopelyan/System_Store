package br.com.scopel.pfv.beta.exception;

/**
 * Quantidade/peso inválido ao adicionar um item à venda (RN05):
 * valor não positivo, ou fração em produto vendido por unidade.
 * A mensagem já vem em português, pronta para exibir ao operador.
 */
public class InvalidQuantityException extends RuntimeException {

    public InvalidQuantityException(String message) {
        super(message);
    }
}
