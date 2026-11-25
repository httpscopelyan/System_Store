package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.function.Consumer;

public final class DrawerShortcut {

    private static final long DEBOUNCE_NANOS = 2_000_000_000L;

    private final PrinterPort printer;
    private final Consumer<String> hint;
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
        lastAcceptedAt = now;
        BackgroundPrinting.runDrawer(printer::openDrawer,
                message -> hint.accept("Não foi possível abrir a gaveta: " + message));
    }
}
