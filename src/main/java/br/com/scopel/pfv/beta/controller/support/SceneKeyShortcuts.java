package br.com.scopel.pfv.beta.controller.support;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.Map;

public final class SceneKeyShortcuts {

    private final Map<KeyCode, Runnable> actions;
    private final EventHandler<KeyEvent> filter = this::handle;

    private SceneKeyShortcuts(Map<KeyCode, Runnable> actions) {
        this.actions = actions;
    }

    public static void install(Node pageNode, Map<KeyCode, Runnable> actions) {
        SceneKeyShortcuts shortcuts = new SceneKeyShortcuts(actions);
        pageNode.sceneProperty().addListener((observable, oldScene, newScene) -> {
            shortcuts.detach(oldScene);
            shortcuts.attach(newScene);
        });
        shortcuts.attach(pageNode.getScene());
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
        Runnable action = actions.get(event.getCode());
        if (action != null) {
            event.consume();
            action.run();
        }
    }
}
