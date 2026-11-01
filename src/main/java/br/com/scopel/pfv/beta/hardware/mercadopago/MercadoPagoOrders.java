package br.com.scopel.pfv.beta.hardware.mercadopago;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import br.com.scopel.pfv.beta.hardware.CardPaymentStatus;
import br.com.scopel.pfv.beta.model.CardType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class MercadoPagoOrders {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MercadoPagoOrders() {
    }

    static String cardBody(BigDecimal amount, CardType type, String reference, String terminalId) {
        String paymentType = type == CardType.DEBITO ? "debit_card" : "credit_card";
        return body(amount, paymentType, type == CardType.CREDITO, reference, terminalId);
    }

    static String pixBody(BigDecimal amount, String reference, String terminalId) {
        return body(amount, "qr", false, reference, terminalId);
    }

    private static String body(BigDecimal amount, String paymentType, boolean oneInstallment, String reference,
                               String terminalId) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "point");
        root.put("external_reference", reference);
        root.putObject("transactions").putArray("payments")
                .addObject().put("amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        ObjectNode config = root.putObject("config");
        ObjectNode point = config.putObject("point");
        point.put("terminal_id", terminalId);
        point.put("print_on_terminal", "no_ticket");
        ObjectNode paymentMethod = config.putObject("payment_method");
        paymentMethod.put("default_type", paymentType);
        if (oneInstallment) {
            paymentMethod.put("default_installments", 1);
        }
        return root.toString();
    }

    static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body == null ? "" : body);
        } catch (Exception invalidJson) {
            throw new CardGatewayException("Resposta inválida do Mercado Pago.", invalidJson);
        }
    }

    static CardPaymentStatus statusOf(JsonNode order) {
        return switch (order.path("status").asText("")) {
            case "processed" -> CardPaymentStatus.APPROVED;
            case "failed" -> CardPaymentStatus.DECLINED;
            case "canceled", "expired", "refunded" -> CardPaymentStatus.CANCELED;
            default -> CardPaymentStatus.PENDING;
        };
    }

    static CardGatewayException failure(ApiResponse response) {
        String code = firstErrorCode(response.body());
        return new CardGatewayException(switch (code) {
            case "already_queued_order_for_terminal" ->
                    "A maquininha já tem uma cobrança em espera. Finalize ou cancele nela e tente de novo.";
            case "forbidden_checking_terminal_owner" -> "A maquininha não pertence a esta conta do Mercado Pago.";
            case "idempotency_key_already_used" -> "Cobrança repetida. Tente novamente.";
            default -> switch (response.status()) {
                case 401, 403 -> "Mercado Pago recusou o acesso. Confira o MP_ACCESS_TOKEN no .env.";
                case 404 -> "Cobrança ou maquininha não encontrada no Mercado Pago.";
                default -> "Mercado Pago respondeu com erro " + response.status()
                        + (code.isEmpty() ? "." : " (" + code + ").");
            };
        });
    }

    private static String firstErrorCode(String body) {
        try {
            return MAPPER.readTree(body == null ? "" : body).path("errors").path(0).path("code").asText("");
        } catch (Exception invalidJson) {
            return "";
        }
    }
}
