package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public final class DirectChargeKeyboard {

    public static final String BACKSPACE_KEY = "⌫";
    public static final String CLEAR_KEY = "C";
    public static final String COMMA_KEY = ",";

    public interface Actions {
        void pressKeypad(String key);

        void onEnter();

        void onDebit();

        void onCredit();

        void onPix();

        void onBack();

        void onCancelCharge();

        void onLeaveNote();
    }

    private final Actions actions;

    public static void apply(AmountInput input, String key) {
        switch (key) {
            case BACKSPACE_KEY -> input.backspace();
            case CLEAR_KEY -> input.clear();
            case COMMA_KEY -> input.comma();
            default -> input.digit(key.charAt(0));
        }
    }

    public DirectChargeKeyboard(Actions actions) {
        this.actions = actions;
    }

    public void handle(KeyEvent event, DirectChargePanels.State state, boolean typingNote) {
        boolean handled = switch (state) {
            case INPUT -> typingNote ? handleNoteKey(event.getCode()) : handleAmountKey(event);
            case CHOOSE -> handleChoiceKey(event.getCode());
            case CHARGING -> handleChargingKey(event.getCode());
        };
        if (handled) {
            event.consume();
        }
    }

    private boolean handleNoteKey(KeyCode code) {
        if (code == KeyCode.ENTER) {
            actions.onEnter();
            return true;
        }
        if (code == KeyCode.ESCAPE) {
            actions.onLeaveNote();
            return true;
        }
        return false;
    }

    private boolean handleAmountKey(KeyEvent event) {
        String text = event.getText();
        KeyCode code = event.getCode();
        if (code == KeyCode.ENTER) {
            actions.onEnter();
        } else if (code == KeyCode.BACK_SPACE) {
            actions.pressKeypad(BACKSPACE_KEY);
        } else if (code == KeyCode.ESCAPE) {
            actions.pressKeypad(CLEAR_KEY);
        } else if (code == KeyCode.DECIMAL || COMMA_KEY.equals(text) || ".".equals(text)) {
            actions.pressKeypad(COMMA_KEY);
        } else if (text != null && text.length() == 1 && Character.isDigit(text.charAt(0))) {
            actions.pressKeypad(text);
        } else {
            return false;
        }
        return true;
    }

    private boolean handleChoiceKey(KeyCode code) {
        switch (code) {
            case DIGIT1, NUMPAD1 -> actions.onDebit();
            case DIGIT2, NUMPAD2 -> actions.onCredit();
            case DIGIT3, NUMPAD3 -> actions.onPix();
            case ESCAPE -> actions.onBack();
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean handleChargingKey(KeyCode code) {
        if (code == KeyCode.ESCAPE) {
            actions.onCancelCharge();
            return true;
        }
        return false;
    }
}
