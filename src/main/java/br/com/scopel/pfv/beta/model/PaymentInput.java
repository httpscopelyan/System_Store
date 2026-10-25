package br.com.scopel.pfv.beta.model;

import java.math.BigDecimal;

/**
 * Entrada de um pagamento no pagamento múltiplo (RN18): forma + valor.
 * Não é entidade; é só o dado de entrada para SaleService.payMixed.
 */
public record PaymentInput(PaymentMethod method, BigDecimal amount) {
}
