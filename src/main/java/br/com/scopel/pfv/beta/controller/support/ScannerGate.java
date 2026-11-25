package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.Page;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ScannerGate {

    static final long BURST_GAP_NANOS = 45_000_000L;
    static final long SWALLOW_NANOS = 250_000_000L;
    private static final List<String> CODE_FIELD_IDS = List.of("codeField", "codeFilter", "productCodeField");
    private static final String DISABLED_HINT = "Leitor desativado nesta tela.";

    private final Supplier<Page> currentPage;
    private final Consumer<String> notice;

    private Scene scene;
    private long lastCharacterAt;
    private long swallowUntil;
    private boolean burstActive;
    private boolean blocking;
    private boolean hintShown;

    public ScannerGate(Supplier<Page> currentPage, Consumer<String> notice) {
        this.currentPage = currentPage;
        this.notice = notice;
        this.lastCharacterAt = System.nanoTime() - BURST_GAP_NANOS * 10;
    }

    public void install(Scene scene) {
        this.scene = scene;
        scene.addEventFilter(KeyEvent.ANY, this::onKey);
    }

    private void onKey(KeyEvent event) {
        Page page = currentPage.get();
        if (page == null) {
            return;
        }
        long now = System.nanoTime();
        if (event.getEventType() == KeyEvent.KEY_PRESSED) {
            onPressed(event, page, now);
        } else if (blocking && burstActive && now < swallowUntil) {
            event.consume();
        }
    }

    private void onPressed(KeyEvent event, Page page, long now) {
        if (!isCharacterKey(event)) {
            return;
        }
        boolean fastAfterPrevious = now - lastCharacterAt < BURST_GAP_NANOS;
        lastCharacterAt = now;
        if (!fastAfterPrevious) {
            burstActive = false;
            blocking = false;
            hintShown = false;
            return;
        }
        burstActive = true;
        swallowUntil = now + SWALLOW_NANOS;
        route(event, page);
    }

    private void route(KeyEvent event, Page page) {
        if (hasModifier(event)) {
            block(event);
            return;
        }
        if (scene.getFocusOwner() instanceof TextInputControl) {
            blocking = false;
            return;
        }
        if (!page.acceptsScanner()) {
            block(event);
            if (!hintShown) {
                hintShown = true;
                notice.accept(DISABLED_HINT);
            }
            return;
        }
        TextField target = findCodeField(scene);
        if (target == null) {
            block(event);
            return;
        }
        blocking = false;
        target.requestFocus();
        event.consume();
        Event.fireEvent(target, event.copyFor(event.getSource(), target));
    }

    private void block(KeyEvent event) {
        blocking = true;
        event.consume();
    }

    private static boolean isCharacterKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            return true;
        }
        String text = event.getText();
        return text != null && !text.isEmpty();
    }

    private static boolean hasModifier(KeyEvent event) {
        return event.isControlDown() || event.isAltDown() || event.isMetaDown();
    }

    private static TextField findCodeField(Scene scene) {
        for (String id : CODE_FIELD_IDS) {
            if (scene.getRoot().lookup("#" + id) instanceof TextField field
                    && field.isVisible() && !field.isDisabled() && field.isEditable()) {
                return field;
            }
        }
        return null;
    }
}
