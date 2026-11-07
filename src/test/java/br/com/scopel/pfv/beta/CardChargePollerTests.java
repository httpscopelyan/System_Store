package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.controller.support.CardChargePoller;
import br.com.scopel.pfv.beta.exception.CardGatewayException;
import br.com.scopel.pfv.beta.hardware.CardChargeResult;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.hardware.CardPaymentStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardChargePollerTests {

    @BeforeAll
    static void toolkit() {
        FxTestSupport.startToolkit();
    }

    private CardPaymentGateway gatewayReporting(CardPaymentStatus first, CardPaymentStatus... next) {
        CardPaymentGateway gateway = mock(CardPaymentGateway.class);
        when(gateway.createCharge(any(), any())).thenReturn(new CardChargeResult("order-1", CardPaymentStatus.PENDING));
        when(gateway.getStatus("order-1")).thenReturn(first, next);
        return gateway;
    }

    @Test
    void aSingleDeclinedReadingDoesNotEndTheChargeWhenTheNextOneIsApproved() throws Exception {
        CardPaymentGateway gateway = gatewayReporting(CardPaymentStatus.DECLINED, CardPaymentStatus.APPROVED);
        CountDownLatch approved = new CountDownLatch(1);
        AtomicReference<String> ended = new AtomicReference<>();

        CardChargePoller poller = new CardChargePoller(gateway, message -> { }, approved::countDown, ended::set);
        poller.start(() -> gateway.createCharge(null, null));

        assertTrue(approved.await(10, TimeUnit.SECONDS), "o pagamento aprovado depois precisa ser registrado");
        assertEquals(null, ended.get());
    }

    @Test
    void twoConsecutiveDeclinedReadingsEndTheCharge() throws Exception {
        CardPaymentGateway gateway = gatewayReporting(CardPaymentStatus.DECLINED);
        CountDownLatch ended = new CountDownLatch(1);

        CardChargePoller poller = new CardChargePoller(gateway, message -> { }, () -> { }, message -> ended.countDown());
        poller.start(() -> gateway.createCharge(null, null));

        assertTrue(ended.await(10, TimeUnit.SECONDS));
    }

    @Test
    void cancelThatFailsBecauseTheCustomerAlreadyPaidRegistersTheSale() throws Exception {
        CardPaymentGateway gateway = mock(CardPaymentGateway.class);
        when(gateway.createCharge(any(), any())).thenReturn(new CardChargeResult("order-1", CardPaymentStatus.PENDING));
        when(gateway.getStatus("order-1")).thenReturn(CardPaymentStatus.PENDING, CardPaymentStatus.APPROVED);
        doThrow(new CardGatewayException("já processada")).when(gateway).cancel(eq("order-1"));
        CountDownLatch approved = new CountDownLatch(1);
        AtomicInteger canceled = new AtomicInteger();

        CardChargePoller poller = new CardChargePoller(gateway, message -> { }, approved::countDown, message -> { });
        poller.start(() -> gateway.createCharge(null, null));
        Thread.sleep(300);
        poller.cancel(canceled::incrementAndGet, message -> { });

        assertTrue(approved.await(10, TimeUnit.SECONDS), "venda paga não pode ser perdida ao cancelar");
        assertFalse(canceled.get() > 0);
    }

    @Test
    void cancelAfterTheChargeAlreadyFinishedReportsInsteadOfThrowing() throws Exception {
        CardPaymentGateway gateway = gatewayReporting(CardPaymentStatus.APPROVED);
        CountDownLatch approved = new CountDownLatch(1);
        CountDownLatch reported = new CountDownLatch(1);

        CardChargePoller poller = new CardChargePoller(gateway, message -> { }, approved::countDown, message -> { });
        poller.start(() -> gateway.createCharge(null, null));
        assertTrue(approved.await(10, TimeUnit.SECONDS));
        Thread.sleep(200);

        poller.cancel(() -> { }, message -> reported.countDown());

        assertTrue(reported.await(5, TimeUnit.SECONDS));
    }
}
