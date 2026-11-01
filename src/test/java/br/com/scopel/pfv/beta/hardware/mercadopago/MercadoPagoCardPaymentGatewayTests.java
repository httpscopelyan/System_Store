package br.com.scopel.pfv.beta.hardware.mercadopago;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import br.com.scopel.pfv.beta.hardware.CardChargeResult;
import br.com.scopel.pfv.beta.hardware.CardPaymentStatus;
import br.com.scopel.pfv.beta.model.CardType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoCardPaymentGatewayTests {

    private static final String TERMINAL = "PAX_A910__SMARTPOS1492194069";

    private static final class FakeApi implements MercadoPagoApi {
        boolean credentials = true;
        ApiResponse next = new ApiResponse(201, "{\"id\":\"ORD1\",\"status\":\"created\"}");
        final List<String> methods = new ArrayList<>();
        final List<String> paths = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();

        @Override
        public boolean hasCredentials() {
            return credentials;
        }

        @Override
        public ApiResponse send(String method, String path, String jsonBody, Map<String, String> extraHeaders) {
            methods.add(method);
            paths.add(path);
            bodies.add(jsonBody);
            headers.add(extraHeaders);
            return next;
        }
    }

    private final FakeApi api = new FakeApi();
    private final MercadoPagoCardPaymentGateway gateway = new MercadoPagoCardPaymentGateway(api, TERMINAL);

    @Test
    void createsDebitChargeWithTwoDecimalAmountAndTerminal() throws Exception {
        CardChargeResult result = gateway.createCharge(new BigDecimal("12.5"), CardType.DEBITO);

        assertEquals("ORD1", result.chargeId());
        assertEquals(CardPaymentStatus.PENDING, result.status());
        assertEquals("POST", api.methods.get(0));
        assertEquals("/v1/orders", api.paths.get(0));
        assertTrue(api.headers.get(0).containsKey("X-Idempotency-Key"));
        JsonNode body = new ObjectMapper().readTree(api.bodies.get(0));
        assertEquals("point", body.path("type").asText());
        assertEquals("12.50", body.path("transactions").path("payments").path(0).path("amount").asText());
        assertEquals(TERMINAL, body.path("config").path("point").path("terminal_id").asText());
        assertEquals("debit_card", body.path("config").path("payment_method").path("default_type").asText());
        assertTrue(body.path("config").path("payment_method").path("default_installments").isMissingNode());
    }

    @Test
    void createsCreditChargeInOneInstallment() throws Exception {
        gateway.createCharge(new BigDecimal("30.00"), CardType.CREDITO);

        JsonNode method = new ObjectMapper().readTree(api.bodies.get(0)).path("config").path("payment_method");
        assertEquals("credit_card", method.path("default_type").asText());
        assertEquals(1, method.path("default_installments").asInt());
    }

    @Test
    void createsPixChargeAsQrWithoutInstallments() throws Exception {
        CardChargeResult result = gateway.createPixCharge(new BigDecimal("7.9"));

        assertEquals("ORD1", result.chargeId());
        JsonNode body = new ObjectMapper().readTree(api.bodies.get(0));
        assertEquals("7.90", body.path("transactions").path("payments").path(0).path("amount").asText());
        assertEquals(TERMINAL, body.path("config").path("point").path("terminal_id").asText());
        JsonNode method = body.path("config").path("payment_method");
        assertEquals("qr", method.path("default_type").asText());
        assertTrue(method.path("default_installments").isMissingNode());
    }

    @Test
    void pixChargeWithoutCredentialsRefuses() {
        api.credentials = false;

        assertThrows(CardGatewayException.class, () -> gateway.createPixCharge(BigDecimal.ONE));
        assertTrue(api.paths.isEmpty());
    }

    @Test
    void usesDifferentIdempotencyKeyPerCharge() {
        gateway.createCharge(BigDecimal.TEN, CardType.DEBITO);
        gateway.createCharge(BigDecimal.TEN, CardType.DEBITO);

        assertNotEquals(api.headers.get(0).get("X-Idempotency-Key"), api.headers.get(1).get("X-Idempotency-Key"));
    }

    @Test
    void mapsOrderStatuses() {
        assertEquals(CardPaymentStatus.PENDING, statusFor("created"));
        assertEquals(CardPaymentStatus.PENDING, statusFor("at_terminal"));
        assertEquals(CardPaymentStatus.APPROVED, statusFor("processed"));
        assertEquals(CardPaymentStatus.DECLINED, statusFor("failed"));
        assertEquals(CardPaymentStatus.CANCELED, statusFor("canceled"));
        assertEquals(CardPaymentStatus.CANCELED, statusFor("expired"));
        assertEquals(CardPaymentStatus.PENDING, statusFor("algo_novo"));
    }

    @Test
    void cancelSendsAtTerminalHeader() {
        api.next = new ApiResponse(202, "{}");

        gateway.cancel("ORD1");

        assertEquals("/v1/orders/ORD1/cancel", api.paths.get(0));
        assertEquals("at_terminal", api.headers.get(0).get("x-allow-cancelable-status"));
    }

    @Test
    void queuedTerminalGivesPortugueseMessage() {
        api.next = new ApiResponse(409, "{\"errors\":[{\"code\":\"already_queued_order_for_terminal\"}]}");

        CardGatewayException failure = assertThrows(CardGatewayException.class,
                () -> gateway.createCharge(BigDecimal.ONE, CardType.DEBITO));

        assertTrue(failure.getMessage().contains("já tem uma cobrança em espera"));
    }

    @Test
    void unauthorizedPointsToToken() {
        api.next = new ApiResponse(401, "{}");

        CardGatewayException failure = assertThrows(CardGatewayException.class, () -> gateway.getStatus("ORD1"));

        assertTrue(failure.getMessage().contains("MP_ACCESS_TOKEN"));
    }

    @Test
    void withoutCredentialsIsNotIntegratedAndRefusesToCharge() {
        api.credentials = false;

        assertFalse(gateway.isIntegrated());
        assertThrows(CardGatewayException.class, () -> gateway.createCharge(BigDecimal.ONE, CardType.DEBITO));
        assertTrue(api.paths.isEmpty());
    }

    @Test
    void withoutTerminalIsNotIntegrated() {
        assertFalse(new MercadoPagoCardPaymentGateway(api, " ").isIntegrated());
        assertTrue(gateway.isIntegrated());
    }

    private CardPaymentStatus statusFor(String orderStatus) {
        api.next = new ApiResponse(200, "{\"id\":\"ORD1\",\"status\":\"" + orderStatus + "\"}");
        return gateway.getStatus("ORD1");
    }
}
