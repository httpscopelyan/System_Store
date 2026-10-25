package br.com.scopel.pfv.beta.hardware;

import br.com.scopel.pfv.beta.model.CardType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação FAKE/MOCK da {@link CardPaymentGateway} para desenvolvimento e
 * teste: NÃO fala com maquininha nenhuma. Simula o ciclo de vida de uma cobrança
 * na Mercado Pago Point Smart (PENDING → APPROVED) para o Frontend conseguir
 * montar a tela de polling e o QA testar sem hardware.
 *
 * <p>É o bean padrão ({@code @Primary}) enquanto não existe aparelho físico nem
 * Access Token/device id. A implementação REAL (Mercado Pago Orders API —
 * {@code POST /v1/orders} com polling, ADR 0007) entra como outro
 * {@code CardPaymentGateway} no futuro, e este mock sai do {@code @Primary}.</p>
 *
 * <p><b>Como decide o tempo até APPROVED:</b> cada cobrança guarda o instante de
 * criação. {@link #getStatus} devolve {@link CardPaymentStatus#PENDING} enquanto
 * {@code agora - criação < approveAfterMillis} e {@link CardPaymentStatus#APPROVED}
 * depois disso (simulando o cliente aproximando o cartão). O atraso é fixo e
 * configurável por {@code pdv.card.mock.approve-after-millis} (padrão 2500 ms),
 * então o comportamento é determinístico — em teste dá para injetar um
 * {@link Clock} controlado e/ou um atraso 0 para aprovar na hora.</p>
 */
// INTEGRAÇÃO DE CARTÃO DESLIGADA (ver ADR 0007/0013 — F4).
// O PDV não conversa mais com a maquininha: o cartão é confirmado manualmente
// pelo operador (PagamentoEsperaController). Para religar a integração no futuro,
// descomente @Component, @Primary e o @Autowired do construtor abaixo.
// @Component
// @Primary
public class MockCardPaymentGateway implements CardPaymentGateway {

    /** Tempo fixo (ms) de PENDING antes de virar APPROVED. */
    private final long approveAfterMillis;
    private final Clock clock;

    /** Estado por cobrança, acessível de várias threads (UI pollando). */
    private final ConcurrentHashMap<String, Charge> charges = new ConcurrentHashMap<>();

    // @Autowired  // religar junto com @Component/@Primary (há 2 construtores)
    public MockCardPaymentGateway(
            @Value("${pdv.card.mock.approve-after-millis:2500}") long approveAfterMillis) {
        this(approveAfterMillis, Clock.systemUTC());
    }

    /** Construtor para teste: permite Clock controlado (determinismo total). */
    public MockCardPaymentGateway(long approveAfterMillis, Clock clock) {
        this.approveAfterMillis = approveAfterMillis;
        this.clock = clock;
    }

    @Override
    public CardChargeResult createCharge(BigDecimal amount, CardType type) {
        String chargeId = UUID.randomUUID().toString();
        charges.put(chargeId, new Charge(amount, type, clock.millis()));
        System.out.printf("[mock-cartao] cobranca %s criada: %s %s -> PENDING%n",
                chargeId, type, amount);
        return new CardChargeResult(chargeId, CardPaymentStatus.PENDING);
    }

    @Override
    public CardPaymentStatus getStatus(String chargeId) {
        Charge charge = charges.get(chargeId);
        if (charge == null) {
            // cobrança desconhecida (nunca criada ou já descartada): trata como cancelada.
            return CardPaymentStatus.CANCELED;
        }
        if (charge.status == CardPaymentStatus.PENDING
                && clock.millis() - charge.createdAtMillis >= approveAfterMillis) {
            charge.status = CardPaymentStatus.APPROVED;
        }
        return charge.status;
    }

    @Override
    public void cancel(String chargeId) {
        Charge charge = charges.get(chargeId);
        if (charge != null && charge.status == CardPaymentStatus.PENDING) {
            charge.status = CardPaymentStatus.CANCELED;
            System.out.printf("[mock-cartao] cobranca %s cancelada%n", chargeId);
        }
    }

    /** Estado mutável interno de uma cobrança simulada. */
    private static final class Charge {
        final BigDecimal amount;
        final CardType type;
        final long createdAtMillis;
        volatile CardPaymentStatus status;

        Charge(BigDecimal amount, CardType type, long createdAtMillis) {
            this.amount = amount;
            this.type = type;
            this.createdAtMillis = createdAtMillis;
            this.status = CardPaymentStatus.PENDING;
        }
    }
}
