package br.com.scopel.pfv.beta.hardware.mercadopago;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class HttpMercadoPagoApi implements MercadoPagoApi {

    private static final String BASE_URL = "https://api.mercadopago.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final String accessToken;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    public HttpMercadoPagoApi(@Value("${MP_ACCESS_TOKEN:}") String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    @Override
    public boolean hasCredentials() {
        return !accessToken.isEmpty();
    }

    @Override
    public ApiResponse send(String method, String path, String jsonBody, Map<String, String> extraHeaders) {
        HttpRequest.BodyPublisher body = jsonBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .method(method, body);
        extraHeaders.forEach(request::header);
        try {
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(), response.body());
        } catch (IOException connectionFailure) {
            throw new CardGatewayException("Sem conexão com o Mercado Pago. Verifique a internet.", connectionFailure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CardGatewayException("Operação com a maquininha interrompida.", interrupted);
        }
    }
}
