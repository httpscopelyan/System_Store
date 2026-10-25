package br.com.scopel.pfv.beta.hardware;

import br.com.scopel.pfv.beta.model.CardType;

import java.math.BigDecimal;

/**
 * Porta de saída da maquininha de cartão (Mercado Pago Point Smart). A decisão de
 * cobrar, o cálculo do valor e o que fazer com cada status ficam nos services; o
 * adaptador físico implementa esta interface sem tocar na regra de negócio (RNF13).
 *
 * O modelo é de pagamento assíncrono por polling (ADR 0007 — sem webhook, sem IP
 * público): o service cria a cobrança e depois consulta o status repetidamente até
 * sair de {@code PENDING}. A implementação real usará a Mercado Pago Orders API
 * ({@code POST /v1/orders}, terminal em modo PDV); por ora o bean padrão é o
 * {@link MockCardPaymentGateway}.
 *
 * Valores monetários sempre em {@link BigDecimal} (regra inviolável — nunca double).
 */
public interface CardPaymentGateway {

    /**
     * Aciona a maquininha com o valor total da compra e o tipo de cartão.
     * Devolve o id da cobrança gerado e o status inicial (normalmente
     * {@link CardPaymentStatus#PENDING}).
     *
     * @param amount valor total a cobrar (BigDecimal, nunca negativo).
     * @param type   débito ou crédito.
     * @return id da cobrança + status inicial.
     */
    CardChargeResult createCharge(BigDecimal amount, CardType type);

    /**
     * Consulta o status atual de uma cobrança. É o método que a tela de espera
     * vai "pollar" até o pagamento sair de {@link CardPaymentStatus#PENDING}.
     *
     * @param chargeId id devolvido por {@link #createCharge}.
     * @return status atual da cobrança.
     */
    CardPaymentStatus getStatus(String chargeId);

    /**
     * Cancela uma cobrança pendente (RF31 — operador desiste antes do cliente pagar).
     *
     * @param chargeId id devolvido por {@link #createCharge}.
     */
    void cancel(String chargeId);
}
