package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.Navigator;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public final class EscapeToBack {

    private final Navigator navigator;
    private final EventHandler<KeyEvent> filter = this::handle;

    private EscapeToBack(Navigator navigator) {
        this.navigator = navigator;
    }

    public static void install(Node pageNode, Navigator navigator) {
        EscapeToBack escapeToBack = new EscapeToBack(navigator);
        pageNode.sceneProperty().addListener((observable, oldScene, newScene) -> {
            escapeToBack.detach(oldScene);
            escapeToBack.attach(newScene);
        });
        escapeToBack.attach(pageNode.getScene());
    }

    private void attach(Scene scene) {
        if (scene != null) {
            scene.addEventFilter(KeyEvent.KEY_PRESSED, filter);
        }
    }

    private void detach(Scene scene) {
        if (scene != null) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, filter);
        }
    }

    private void handle(KeyEvent event) {
        if (isOpenComboBox(event.getTarget()) || !isBackKey(event)) {
            return;
        }
        event.consume();
        Platform.runLater(navigator::back);
    }

    private boolean isBackKey(KeyEvent event) {
        boolean typing = event.getTarget() instanceof TextInputControl;
        if (typing) {
            return event.getCode() == KeyCode.ESCAPE;
        }
        return event.getCode() == KeyCode.V && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown();
    }

    private boolean isOpenComboBox(Object target) {
        return target instanceof ComboBoxBase<?> comboBox && comboBox.isShowing();
    }
}
