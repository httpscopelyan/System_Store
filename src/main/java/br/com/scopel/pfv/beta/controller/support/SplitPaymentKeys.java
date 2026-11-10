package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SplitPaymentKeys {

    private final Supplier<SplitStep> currentStep;
    private final Runnable onBack;
    private final Runnable onConfirm;
    private final Runnable onRemoveLastCashPart;
    private final Consumer<SplitPartMethod> onChooseMethod;

    public SplitPaymentKeys(Supplier<SplitStep> currentStep, Runnable onBack, Runnable onConfirm,
                            Runnable onRemoveLastCashPart, Consumer<SplitPartMethod> onChooseMethod) {
        this.currentStep = currentStep;
        this.onBack = onBack;
        this.onConfirm = onConfirm;
        this.onRemoveLastCashPart = onRemoveLastCashPart;
        this.onChooseMethod = onChooseMethod;
    }

    public void install(Node root) {
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handle);
    }

    private void handle(KeyEvent event) {
        KeyCode code = event.getCode();
        boolean choosingMethod = currentStep.get() == SplitStep.CHOOSING_METHOD;
        SplitPartMethod keyedMethod = choosingMethod ? SplitPartMethod.fromKey(code) : null;
        if (code == KeyCode.ESCAPE) {
            onBack.run();
        } else if (code == KeyCode.ENTER) {
            onConfirm.run();
        } else if (choosingMethod && code == KeyCode.DELETE) {
            onRemoveLastCashPart.run();
        } else if (keyedMethod != null) {
            onChooseMethod.accept(keyedMethod);
        } else {
            return;
        }
        event.consume();
    }
}
