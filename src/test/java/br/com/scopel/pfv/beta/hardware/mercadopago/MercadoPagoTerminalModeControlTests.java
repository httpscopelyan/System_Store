package br.com.scopel.pfv.beta.hardware.mercadopago;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoTerminalModeControlTests {

    private static final String TERMINAL = "PAX_A910__SMARTPOS1492194069";

    private static final class FakeApi implements MercadoPagoApi {
        boolean credentials = true;
        ApiResponse next = new ApiResponse(200, "{}");
        final List<String> methods = new ArrayList<>();
        final List<String> paths = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();

        @Override
        public boolean hasCredentials() {
            return credentials;
        }

        @Override
        public ApiResponse send(String method, String path, String jsonBody, Map<String, String> extraHeaders) {
            methods.add(method);
            paths.add(path);
            bodies.add(jsonBody);
            return next;
        }
    }

    private final FakeApi api = new FakeApi();
    private final MercadoPagoTerminalModeControl control = new MercadoPagoTerminalModeControl(api, TERMINAL);

    private static String listWith(String id, String mode) {
        return "{\"data\":{\"terminals\":[{\"id\":\"OTHER\",\"operating_mode\":\"STANDALONE\"},"
                + "{\"id\":\"" + id + "\",\"operating_mode\":\"" + mode + "\"}]}}";
    }

    @Test
    void readsModeOfConfiguredTerminalOnly() {
        api.next = new ApiResponse(200, listWith(TERMINAL, "PDV"));

        assertEquals(TerminalMode.PDV, control.currentMode());
        assertEquals("GET", api.methods.get(0));
        assertEquals("/terminals/v1/list", api.paths.get(0));
    }

    @Test
    void readsStandaloneAndUnknownModes() {
        api.next = new ApiResponse(200, listWith(TERMINAL, "STANDALONE"));
        assertEquals(TerminalMode.STANDALONE, control.currentMode());

        api.next = new ApiResponse(200, listWith(TERMINAL, "ALGO_NOVO"));
        assertEquals(TerminalMode.UNKNOWN, control.currentMode());
    }

    @Test
    void terminalMissingFromAccountFails() {
        api.next = new ApiResponse(200, listWith("OUTRA", "PDV"));

        CardGatewayException failure = assertThrows(CardGatewayException.class, control::currentMode);

        assertTrue(failure.getMessage().contains("não foi encontrada"));
    }

    @Test
    void switchSendsPatchWithTerminalAndMode() throws Exception {
        control.switchTo(TerminalMode.STANDALONE);

        assertEquals("PATCH", api.methods.get(0));
        assertEquals("/terminals/v1/setup", api.paths.get(0));
        JsonNode terminal = new ObjectMapper().readTree(api.bodies.get(0)).path("terminals").path(0);
        assertEquals(TERMINAL, terminal.path("id").asText());
        assertEquals("STANDALONE", terminal.path("operating_mode").asText());
    }

    @Test
    void switchToUnknownIsRefusedWithoutCallingApi() {
        assertThrows(IllegalArgumentException.class, () -> control.switchTo(TerminalMode.UNKNOWN));
        assertTrue(api.paths.isEmpty());
    }

    @Test
    void apiErrorSurfacesAsGatewayException() {
        api.next = new ApiResponse(401, "{}");

        CardGatewayException failure = assertThrows(CardGatewayException.class,
                () -> control.switchTo(TerminalMode.PDV));

        assertTrue(failure.getMessage().contains("MP_ACCESS_TOKEN"));
    }

    @Test
    void withoutCredentialsIsUnavailableAndDoesNotCallApi() {
        api.credentials = false;

        assertFalse(control.isAvailable());
        assertThrows(CardGatewayException.class, control::currentMode);
        assertThrows(CardGatewayException.class, () -> control.switchTo(TerminalMode.PDV));
        assertTrue(api.paths.isEmpty());
    }
}
