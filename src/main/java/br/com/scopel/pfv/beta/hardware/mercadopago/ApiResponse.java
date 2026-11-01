package br.com.scopel.pfv.beta.hardware.mercadopago;

public record ApiResponse(int status, String body) {

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
