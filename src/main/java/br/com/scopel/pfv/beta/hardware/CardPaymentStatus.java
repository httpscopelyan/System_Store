package br.com.scopel.pfv.beta.hardware;

/**
 * Estado de uma cobrança na maquininha de cartão (Mercado Pago Point Smart).
 *
 * <ul>
 *   <li>{@code PENDING}  — cobrança criada, aguardando o cliente aproximar/inserir o cartão.</li>
 *   <li>{@code APPROVED} — pagamento aprovado pela adquirente.</li>
 *   <li>{@code DECLINED} — pagamento recusado (sem saldo, cartão inválido etc.).</li>
 *   <li>{@code CANCELED} — cobrança cancelada antes de concluir (RF31).</li>
 * </ul>
 */
public enum CardPaymentStatus {
    PENDING,
    APPROVED,
    DECLINED,
    CANCELED
}
