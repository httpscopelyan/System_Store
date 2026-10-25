package br.com.scopel.pfv.beta.exception;

public class PixNotConfiguredException extends RuntimeException {

    public PixNotConfiguredException() {
        super("Chave PIX não configurada (ConfigADM_PIX).");
    }
}
