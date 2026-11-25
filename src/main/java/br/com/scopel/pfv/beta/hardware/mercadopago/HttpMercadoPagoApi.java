package br.com.scopel.pfv.beta.hardware.mercadopago;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.Map;

@Component
public class HttpMercadoPagoApi implements MercadoPagoApi {

    private static final Logger log = LoggerFactory.getLogger(HttpMercadoPagoApi.class);
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
            log.warn("Falha de rede em {} {}", method, path, connectionFailure);
            throw new CardGatewayException(describe(connectionFailure), connectionFailure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CardGatewayException("Operação com a maquininha interrompida.", interrupted);
        }
    }

    private static String describe(IOException failure) {
        if (failure instanceof UnknownHostException) {
            return "Sem conexão com o Mercado Pago: o computador não encontrou o endereço (DNS). Verifique a internet.";
        }
        if (failure instanceof HttpConnectTimeoutException || failure instanceof ConnectException) {
            return "Sem conexão com o Mercado Pago: não foi possível conectar. Verifique a internet ou o firewall.";
        }
        if (failure instanceof HttpTimeoutException) {
            return "O Mercado Pago demorou demais para responder. A cobrança pode ter sido criada; confira a maquininha.";
        }
        if (failure instanceof SSLException) {
            return "Falha de segurança (TLS) ao falar com o Mercado Pago. Verifique data e hora do computador e antivírus.";
        }
        return "Falha de comunicação com o Mercado Pago (" + failure.getClass().getSimpleName() + "). Verifique a internet.";
    }
}
