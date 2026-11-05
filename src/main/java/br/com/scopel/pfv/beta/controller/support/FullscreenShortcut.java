package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

public final class FullscreenShortcut {

    private FullscreenShortcut() {
    }

    public static void install(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F11 && scene.getWindow() instanceof Stage stage) {
                event.consume();
                stage.setFullScreenExitHint("Pressione F11 ou ESC para sair da tela cheia");
                stage.setFullScreen(!stage.isFullScreen());
            }
        });
    }
}
