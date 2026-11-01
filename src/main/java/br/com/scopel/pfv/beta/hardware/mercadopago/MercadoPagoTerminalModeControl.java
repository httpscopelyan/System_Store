package br.com.scopel.pfv.beta.hardware.mercadopago;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MercadoPagoTerminalModeControl {

    private static final String LIST_PATH = "/terminals/v1/list";
    private static final String SETUP_PATH = "/terminals/v1/setup";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MercadoPagoApi api;
    private final String terminalId;

    public MercadoPagoTerminalModeControl(MercadoPagoApi api, @Value("${MP_TERMINAL_ID:}") String terminalId) {
        this.api = api;
        this.terminalId = terminalId == null ? "" : terminalId.trim();
    }

    public boolean isAvailable() {
        return api.hasCredentials() && !terminalId.isEmpty();
    }

    public TerminalMode currentMode() {
        requireAvailable();
        ApiResponse response = api.send("GET", LIST_PATH, null, Map.of());
        if (!response.isSuccess()) {
            throw MercadoPagoOrders.failure(response);
        }
        for (JsonNode terminal : MercadoPagoOrders.parse(response.body()).path("data").path("terminals")) {
            if (terminalId.equals(terminal.path("id").asText())) {
                return TerminalMode.fromApi(terminal.path("operating_mode").asText(""));
            }
        }
        throw new CardGatewayException("A maquininha configurada não foi encontrada nesta conta do Mercado Pago.");
    }

    public void switchTo(TerminalMode mode) {
        requireAvailable();
        if (mode == TerminalMode.UNKNOWN) {
            throw new IllegalArgumentException("Modo de operação inválido.");
        }
        ObjectNode body = MAPPER.createObjectNode();
        ObjectNode terminal = body.putArray("terminals").addObject();
        terminal.put("id", terminalId);
        terminal.put("operating_mode", mode.name());
        ApiResponse response = api.send("PATCH", SETUP_PATH, body.toString(), Map.of());
        if (!response.isSuccess()) {
            throw MercadoPagoOrders.failure(response);
        }
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new CardGatewayException(
                    "Maquininha não configurada: informe MP_ACCESS_TOKEN e MP_TERMINAL_ID no .env.");
        }
    }
}
