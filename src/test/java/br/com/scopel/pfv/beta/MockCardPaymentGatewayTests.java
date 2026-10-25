package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.hardware.CardChargeResult;
import br.com.scopel.pfv.beta.hardware.CardPaymentStatus;
import br.com.scopel.pfv.beta.hardware.MockCardPaymentGateway;
import br.com.scopel.pfv.beta.model.CardType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste puro (sem Spring): o mock da maquininha é determinístico via Clock
 * injetado, então não precisa de banco nem de Thread.sleep. Usa o construtor de
 * teste {@code MockCardPaymentGateway(long approveAfterMillis, Clock clock)}.
 */
class MockCardPaymentGatewayTests {

    /** Clock controlado: o teste empurra o "agora" sem esperar tempo real. */
    private static final class MutableClock extends Clock {
        private Instant now;
        private final ZoneId zone;

        MutableClock(Instant start) {
            this(start, ZoneOffset.UTC);
        }

        private MutableClock(Instant start, ZoneId zone) {
            this.now = start;
            this.zone = zone;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId z) { return new MutableClock(now, z); }
        @Override public Instant instant() { return now; }
    }

    @Test
    void approveAfterZeroMillisBecomesApprovedImmediately() {
        MockCardPaymentGateway gateway = new MockCardPaymentGateway(0, Clock.systemUTC());

        CardChargeResult result = gateway.createCharge(new BigDecimal("10.00"), CardType.DEBITO);

        assertNotNull(result.chargeId(), "chargeId não pode ser nulo");
        assertEquals(CardPaymentStatus.PENDING, result.status(), "status inicial é PENDING");
        // com limiar 0, a próxima consulta já aprova
        assertEquals(CardPaymentStatus.APPROVED, gateway.getStatus(result.chargeId()));
    }

    @Test
    void stayPendingBeforeThresholdThenApprovedAfterClockAdvances() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        MockCardPaymentGateway gateway = new MockCardPaymentGateway(2500, clock);

        CardChargeResult result = gateway.createCharge(new BigDecimal("55.90"), CardType.CREDITO);
        String id = result.chargeId();

        // antes do limiar: ainda PENDING
        clock.advance(Duration.ofMillis(2499));
        assertEquals(CardPaymentStatus.PENDING, gateway.getStatus(id), "antes de 2500ms continua PENDING");

        // exatamente no limiar (>=): já aprova
        clock.advance(Duration.ofMillis(1));
        assertEquals(CardPaymentStatus.APPROVED, gateway.getStatus(id), "em 2500ms vira APPROVED");
    }

    @Test
    void cancelPendingChargeBecomesCanceled() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        MockCardPaymentGateway gateway = new MockCardPaymentGateway(2500, clock);

        CardChargeResult result = gateway.createCharge(new BigDecimal("30.00"), CardType.DEBITO);
        String id = result.chargeId();

        gateway.cancel(id);

        assertEquals(CardPaymentStatus.CANCELED, gateway.getStatus(id));
    }

    @Test
    void cancelAfterApprovedDoesNotOverrideStatus() {
        MockCardPaymentGateway gateway = new MockCardPaymentGateway(0, Clock.systemUTC());

        CardChargeResult result = gateway.createCharge(new BigDecimal("12.34"), CardType.CREDITO);
        String id = result.chargeId();

        // limiar 0: consulta aprova
        assertEquals(CardPaymentStatus.APPROVED, gateway.getStatus(id));

        // cancel após aprovada NÃO sobrescreve
        gateway.cancel(id);
        assertEquals(CardPaymentStatus.APPROVED, gateway.getStatus(id));
    }

    @Test
    void getStatusOfUnknownChargeReturnsCanceledWithoutThrowing() {
        MockCardPaymentGateway gateway = new MockCardPaymentGateway(0, Clock.systemUTC());

        assertEquals(CardPaymentStatus.CANCELED,
                gateway.getStatus("id-que-nunca-existiu"),
                "chargeId desconhecido -> CANCELED, sem lançar");
    }
}
