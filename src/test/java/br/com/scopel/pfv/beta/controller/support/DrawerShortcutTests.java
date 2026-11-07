package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
    void repeatedPressesInsideTheDebounceWindowFireOnlyOnePulse() throws InterruptedException {
        AtomicInteger pulses = new AtomicInteger();
        DrawerShortcut shortcut = new DrawerShortcut(countingPrinter(pulses, null), message -> { });

        for (int press = 0; press < 20; press++) {
            shortcut.openDrawer();
        }
        Thread.sleep(300);

        assertEquals(1, pulses.get());
    }

    @Test
    void aHungPrinterNeverAccumulatesPulsesWhileItIsBlocked() throws InterruptedException {
        AtomicInteger pulses = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        List<String> hints = new ArrayList<>();
        DrawerShortcut shortcut = new DrawerShortcut(countingPrinter(pulses, release), hints::add);

        shortcut.openDrawer();
        for (int press = 0; press < 5; press++) {
            Thread.sleep(800);
            shortcut.openDrawer();
        }
        release.countDown();
        Thread.sleep(300);

        assertEquals(1, pulses.get(), "F12 durante a trava não pode enfileirar acionamentos");
        assertTrue(hints.stream().anyMatch(message -> message.contains("não está respondendo")));
    }

    @Test
    void drawerWorksAgainOnceThePrinterRecovers() throws InterruptedException {
        AtomicInteger pulses = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        DrawerShortcut shortcut = new DrawerShortcut(countingPrinter(pulses, release), message -> { });

        shortcut.openDrawer();
        release.countDown();
        Thread.sleep(900);
        shortcut.openDrawer();
        Thread.sleep(300);

        assertEquals(2, pulses.get());
    }

    private PrinterPort countingPrinter(AtomicInteger pulses, CountDownLatch blockUntil) {
        return new PrinterPort() {
            @Override public void print(String content) { }

            @Override public void openDrawer() {
                pulses.incrementAndGet();
                if (blockUntil != null) {
                    try {
                        blockUntil.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
    }
}
