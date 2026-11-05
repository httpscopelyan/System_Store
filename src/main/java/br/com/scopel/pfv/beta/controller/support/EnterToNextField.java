package br.com.scopel.pfv.beta.controller.support;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.List;

public final class EnterToNextField {

    private EnterToNextField() {
    }

    public static void install(Node... orderedFields) {
        install(null, orderedFields);
    }

    public static void install(Runnable onLastField, Node... orderedFields) {
        List<Node> fields = List.of(orderedFields);
        if (onLastField != null) {
            fields.get(fields.size() - 1).addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    event.consume();
                    onLastField.run();
                }
            });
        }
        for (int index = 0; index < fields.size() - 1; index++) {
            int current = index;
            fields.get(index).addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    event.consume();
                    focusNextAfter(fields, current);
                }
            });
        }
    }

    private static void focusNextAfter(List<Node> fields, int current) {
        for (int next = current + 1; next < fields.size(); next++) {
            Node candidate = fields.get(next);
            if (candidate.isVisible() && !candidate.isDisabled()) {
                Platform.runLater(() -> {
                    candidate.requestFocus();
                    if (candidate instanceof TextInputControl input) {
                        input.selectAll();
                    }
                });
                return;
            }
        }
    }
}
