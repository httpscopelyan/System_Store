package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.function.Consumer;

public final class DrawerShortcut {

    private final PrinterPort printer;
    private final Consumer<String> hint;

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
        Thread worker = new Thread(() -> {
            try {
                printer.openDrawer();
            } catch (RuntimeException failure) {
                javafx.application.Platform.runLater(() -> hint.accept("Não foi possível abrir a gaveta: " + failure.getMessage()));
            }
        }, "drawer-f12");
        worker.setDaemon(true);
        worker.start();
    }
}
