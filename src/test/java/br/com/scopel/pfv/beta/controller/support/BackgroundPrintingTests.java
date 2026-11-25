package br.com.scopel.pfv.beta.controller.support;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundPrintingTests {

    @Test
    void aSecondPrintWhileOneIsRunningIsRefusedInsteadOfQueued() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CopyOnWriteArrayList<String> secondJobRuns = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> failures = new CopyOnWriteArrayList<>();

        assertTrue(BackgroundPrinting.run(() -> await(release), failures::add));
        boolean accepted = BackgroundPrinting.run(() -> secondJobRuns.add("ran"), failures::add);
        release.countDown();
        waitUntil(() -> !BackgroundPrinting.isPrinting(), 2000);
        Thread.sleep(100);

        assertFalse(accepted);
        assertTrue(secondJobRuns.isEmpty(), "impressão recusada não pode ficar na fila");
        assertEquals(1, failures.size());
    }

    @Test
    void printingFlagIsOnWhileRunningAndOffTheInstantItFinishes() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);

        BackgroundPrinting.run(() -> await(release), message -> { });
        assertTrue(BackgroundPrinting.isPrinting());
        release.countDown();
        waitUntil(() -> !BackgroundPrinting.isPrinting(), 2000);

        assertFalse(BackgroundPrinting.isPrinting());
    }

    @Test
    void printingFlagIsClearedEvenWhenTheJobFails() throws InterruptedException {
        CopyOnWriteArrayList<String> failures = new CopyOnWriteArrayList<>();

        BackgroundPrinting.run(() -> {
            throw new IllegalStateException("impressora offline");
        }, failures::add);
        waitUntil(() -> !BackgroundPrinting.isPrinting() && !failures.isEmpty(), 2000);

        assertFalse(BackgroundPrinting.isPrinting());
        assertEquals("impressora offline", failures.get(0));
        assertTrue(BackgroundPrinting.run(() -> { }, message -> { }));
        waitUntil(() -> !BackgroundPrinting.isPrinting(), 2000);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }
}
