package br.com.scopel.pfv.beta.controller.support;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public final class BlockButtons {

    private BlockButtons() {
    }

    public static void apply(Pane container) {
        for (Node child : container.getChildren()) {
            if (child instanceof Button button) {
                render(button);
                button.textProperty().addListener((obs, old, text) -> render(button));
            }
        }
    }

    private static void render(Button button) {
        String[] lines = button.getText() == null ? new String[0] : button.getText().split("\n");
        if (lines.length < 2) {
            return;
        }
        VBox content = new VBox(2);
        content.setAlignment(Pos.CENTER);
        content.getChildren().add(label(lines[0], "block-icon"));
        content.getChildren().add(label(lines[1], "block-name"));
        if (lines.length > 2) {
            content.getChildren().add(label(lines[2], "block-key"));
        }
        button.setGraphic(content);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
