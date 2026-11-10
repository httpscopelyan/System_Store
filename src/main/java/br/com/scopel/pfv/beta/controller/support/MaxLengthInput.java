package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;

public final class MaxLengthInput {

    private MaxLengthInput() {
    }

    public static void limit(TextInputControl input, int maxLength) {
        input.setTextFormatter(new TextFormatter<String>(change -> {
            int overflow = change.getControlNewText().length() - maxLength;
            if (overflow <= 0) {
                return change;
            }
            String insertedText = change.getText();
            if (insertedText.length() <= overflow) {
                return null;
            }
            change.setText(insertedText.substring(0, insertedText.length() - overflow));
            return change;
        }));
    }
}
