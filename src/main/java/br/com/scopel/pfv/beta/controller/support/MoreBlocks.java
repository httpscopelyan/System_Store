package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;

import java.util.function.Predicate;

public final class MoreBlocks {

    private static final String EXTRA_STYLE = "block-extra";

    private final Pane bar;
    private final Button toggle;
    private final Predicate<Button> allowed;
    private boolean expanded;

    public MoreBlocks(Pane bar, Button toggle, Predicate<Button> allowed) {
        this.bar = bar;
        this.toggle = toggle;
        this.allowed = allowed;
        show(false);
    }

    public void toggle() {
        show(!expanded);
    }

    private void show(boolean expand) {
        expanded = expand;
        for (Node child : bar.getChildren()) {
            if (child instanceof Button button && button.getStyleClass().contains(EXTRA_STYLE)) {
                boolean visible = expand && allowed.test(button);
                button.setVisible(visible);
                button.setManaged(visible);
            }
        }
        toggle.setText(expand ? "«\nMenos" : "»\nMais");
    }
}
