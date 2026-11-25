package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class DrawerShortcutTests {

    @Test
    void f12OpensTheDrawerAndNeverPrints() {
        PrinterPort printer = mock(PrinterPort.class);

        new DrawerShortcut(printer, message -> { }).openDrawer();

        verify(printer, timeout(2000)).openDrawer();
        verify(printer, never()).print(anyString());
    }

    @Test
    void repeatedPressesInsideTheTwoSecondWindowFireOnlyOnePulse() throws InterruptedException {
        AtomicInteger pulses = new AtomicInteger();
        DrawerShortcut shortcut = new DrawerShortcut(countingPrinter(pulses, null), message -> { });

        for (int press = 0; press < 20; press++) {
            shortcut.openDrawer();
            Thread.sleep(50);
        }
        Thread.sleep(300);

        assertEquals(1, pulses.get());
    }

    @Test
    void drawerWorksAgainAfterTheTwoSecondWindow() throws InterruptedException {
        AtomicInteger pulses = new AtomicInteger();
        DrawerShortcut shortcut = new DrawerShortcut(countingPrinter(pulses, null), message -> { });

        shortcut.openDrawer();
        Thread.sleep(2200);
        shortcut.openDrawer();
        Thread.sleep(300);

        assertEquals(2, pulses.get());
    }

    @Test
    void drawerOpensImmediatelyEvenWhileAReceiptIsStillPrinting() throws InterruptedException {
        CountDownLatch releaseReceipt = new CountDownLatch(1);
        AtomicInteger pulses = new AtomicInteger();
        BackgroundPrinting.run(() -> await(releaseReceipt), message -> { });
        try {
            long startedAt = System.nanoTime();
            new DrawerShortcut(countingPrinter(pulses, null), message -> { }).openDrawer();
            waitUntil(() -> pulses.get() == 1, 500);

            assertEquals(1, pulses.get());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) < 500);
        } finally {
            releaseReceipt.countDown();
            waitUntil(() -> !BackgroundPrinting.isPrinting(), 2000);
        }
    }

    @Test
    void aNewPressReplacesAHungPulseInsteadOfWaitingBehindIt() throws InterruptedException {
        AtomicInteger pulses = new AtomicInteger();
        CountDownLatch hang = new CountDownLatch(1);
        PrinterPort hangsOnTheFirstPulseOnly = new PrinterPort() {
            @Override public void print(String content) { }

            @Override public void openDrawer() {
                if (pulses.incrementAndGet() == 1) {
                    await(hang);
                }
            }
        };

        new DrawerShortcut(hangsOnTheFirstPulseOnly, message -> { }).openDrawer();
        waitUntil(() -> pulses.get() == 1, 500);
        new DrawerShortcut(hangsOnTheFirstPulseOnly, message -> { }).openDrawer();
        waitUntil(() -> pulses.get() == 2, 500);

        assertEquals(2, pulses.get());
        hang.countDown();
    }

    private PrinterPort countingPrinter(AtomicInteger pulses, CountDownLatch blockUntil) {
        return new PrinterPort() {
            @Override public void print(String content) { }

            @Override public void openDrawer() {
                pulses.incrementAndGet();
                if (blockUntil != null) {
                    await(blockUntil);
                }
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }
}
