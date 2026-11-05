package br.com.scopel.pfv.beta.hardware.mercadopago;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import br.com.scopel.pfv.beta.hardware.CardChargeResult;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.hardware.CardPaymentStatus;
import br.com.scopel.pfv.beta.model.CardType;
import com.fasterxml.jackson.databind.JsonNode;
import br.com.scopel.pfv.beta.service.AppSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class MercadoPagoCardPaymentGateway implements CardPaymentGateway {

    private static final String ORDERS_PATH = "/v1/orders";

    private final MercadoPagoApi api;
    private final String terminalId;
    private final AppSettingService settings;

    public MercadoPagoCardPaymentGateway(MercadoPagoApi api, String terminalId) {
        this(api, terminalId, null);
    }

    @Autowired
    public MercadoPagoCardPaymentGateway(MercadoPagoApi api, @Value("${MP_TERMINAL_ID:}") String terminalId,
                                         AppSettingService settings) {
        this.api = api;
        this.terminalId = terminalId == null ? "" : terminalId.trim();
        this.settings = settings;
    }

    @Override
    public boolean isIntegrated() {
        return hasTerminal() && !isManualMode();
    }

    private boolean hasTerminal() {
        return api.hasCredentials() && !terminalId.isEmpty();
    }

    private boolean isManualMode() {
        return settings != null && settings.getBoolean(AppSettingService.TERMINAL_MANUAL_MODE, false);
    }

    @Override
    public CardChargeResult createCharge(BigDecimal amount, CardType type) {
        return createOrder(MercadoPagoOrders.cardBody(amount, type, newReference(), terminalId));
    }

    @Override
    public CardChargeResult createPixCharge(BigDecimal amount) {
        return createOrder(MercadoPagoOrders.pixBody(amount, newReference(), terminalId));
    }

    private String newReference() {
        return "pdv-" + UUID.randomUUID();
    }

    private CardChargeResult createOrder(String body) {
        requireIntegrated();
        ApiResponse response = api.send("POST", ORDERS_PATH, body,
                Map.of("X-Idempotency-Key", UUID.randomUUID().toString()));
        if (!response.isSuccess()) {
            throw MercadoPagoOrders.failure(response);
        }
        JsonNode order = MercadoPagoOrders.parse(response.body());
        String orderId = order.path("id").asText("");
        if (orderId.isEmpty()) {
            throw new CardGatewayException("O Mercado Pago não devolveu o identificador da cobrança.");
        }
        return new CardChargeResult(orderId, MercadoPagoOrders.statusOf(order));
    }

    @Override
    public CardPaymentStatus getStatus(String chargeId) {
        requireIntegrated();
        ApiResponse response = api.send("GET", ORDERS_PATH + "/" + chargeId, null, Map.of());
        if (!response.isSuccess()) {
            throw MercadoPagoOrders.failure(response);
        }
        return MercadoPagoOrders.statusOf(MercadoPagoOrders.parse(response.body()));
    }

    @Override
    public void cancel(String chargeId) {
        requireIntegrated();
        ApiResponse response = api.send("POST", ORDERS_PATH + "/" + chargeId + "/cancel", null,
                Map.of("x-allow-cancelable-status", "at_terminal",
                        "X-Idempotency-Key", UUID.randomUUID().toString()));
        if (!response.isSuccess()) {
            throw MercadoPagoOrders.failure(response);
        }
    }

    private void requireIntegrated() {
        if (!hasTerminal()) {
            throw new CardGatewayException(
                    "Maquininha não configurada: informe MP_ACCESS_TOKEN e MP_TERMINAL_ID no .env.");
        }
        if (isManualMode()) {
            throw new CardGatewayException("Maquininha em modo manual: o valor é digitado na própria maquininha.");
        }
    }
}
