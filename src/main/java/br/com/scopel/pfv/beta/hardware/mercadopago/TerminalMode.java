package br.com.scopel.pfv.beta.hardware.mercadopago;

public enum TerminalMode {
    PDV,
    STANDALONE,
    UNKNOWN;

    public static TerminalMode fromApi(String value) {
        if ("PDV".equals(value)) {
            return PDV;
        }
        if ("STANDALONE".equals(value)) {
            return STANDALONE;
        }
        return UNKNOWN;
    }
}
