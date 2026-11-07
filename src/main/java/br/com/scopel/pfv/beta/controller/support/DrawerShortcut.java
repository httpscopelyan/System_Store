package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DrawerShortcut {

    private static final long DEBOUNCE_NANOS = 700_000_000L;
    private static final long UNRESPONSIVE_AFTER_MILLIS = 3_000;

    private final PrinterPort printer;
    private final Consumer<String> hint;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "drawer-f12");
        thread.setDaemon(true);
        return thread;
    });
    private long lastAcceptedAt = System.nanoTime() - DEBOUNCE_NANOS;

    public DrawerShortcut(PrinterPort printer, Consumer<String> hint) {
        this.printer = printer;
        this.hint = hint;
    }

    public void install(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F12) {
                event.consume();
                openDrawer();
            }
        });
    }

    void openDrawer() {
        long now = System.nanoTime();
        if (now - lastAcceptedAt < DEBOUNCE_NANOS) {
            return;
        }
        if (!inFlight.compareAndSet(false, true)) {
            lastAcceptedAt = now;
            hint.accept("A impressora não está respondendo. Verifique energia, cabo e papel (F12 ignorado).");
            return;
        }
        lastAcceptedAt = now;
        worker.execute(this::pulse);
        watchForUnresponsivePrinter();
    }

    private void pulse() {
        try {
            printer.openDrawer();
        } catch (RuntimeException failure) {
            Platform.runLater(() -> hint.accept("Não foi possível abrir a gaveta: " + failure.getMessage()));
        } finally {
            inFlight.set(false);
        }
    }

    private void watchForUnresponsivePrinter() {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(UNRESPONSIVE_AFTER_MILLIS);
            } catch (InterruptedException interrupted) {
                return;
            }
            if (inFlight.get()) {
                Platform.runLater(() -> hint.accept(
                        "A impressora não responde. Verifique energia, cabo e papel; novos F12 serão ignorados até ela voltar."));
            }
        }, "drawer-f12-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
}
