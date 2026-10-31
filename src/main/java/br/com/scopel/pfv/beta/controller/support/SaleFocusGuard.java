package br.com.scopel.pfv.beta.controller.support;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;

import java.util.Set;

public final class SaleFocusGuard {

    private final TextField scanField;
    private final Set<Node> passiveNodes;

    public SaleFocusGuard(TextField scanField, Set<Node> passiveNodes) {
        this.scanField = scanField;
        this.passiveNodes = passiveNodes;
        scanField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused) {
                reclaimLater();
            }
        });
    }

    public static boolean shouldReclaim(Node focusOwner, Set<Node> passiveNodes) {
        return focusOwner == null || passiveNodes.contains(focusOwner);
    }

    public void focusScanField() {
        Platform.runLater(() -> {
            if (scanField.getScene() != null && !scanField.isDisabled()) {
                scanField.requestFocus();
                scanField.selectAll();
            }
        });
    }

    private void reclaimLater() {
        if (scanField.isDisabled() || scanField.getScene() == null) {
            return;
        }
        Platform.runLater(() -> {
            Scene scene = scanField.getScene();
            if (scene != null && shouldReclaim(scene.getFocusOwner(), passiveNodes)) {
                focusScanField();
            }
        });
    }
}
