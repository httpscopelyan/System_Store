package br.com.scopel.pfv.beta.hardware;

/**
 * Resultado de uma interação com a maquininha de cartão.
 *
 * Record imutável: carrega o identificador da cobrança ({@code chargeId}, usado
 * depois para {@code getStatus}/{@code cancel}) e o {@link CardPaymentStatus}
 * daquele instante. Nenhuma regra de negócio aqui (RNF13) — só o dado de hardware.
 *
 * @param chargeId identificador da cobrança na maquininha/gateway.
 * @param status   estado da cobrança no momento da resposta.
 */
public record CardChargeResult(String chargeId, CardPaymentStatus status) {
}
