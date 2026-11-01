package br.com.scopel.pfv.beta.hardware.mercadopago;

import java.util.Map;

public interface MercadoPagoApi {

    boolean hasCredentials();

    ApiResponse send(String method, String path, String jsonBody, Map<String, String> extraHeaders);
}
