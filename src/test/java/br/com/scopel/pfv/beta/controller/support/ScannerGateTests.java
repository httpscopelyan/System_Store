package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.Page;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf("isHeadless")
class ScannerGateTests {

    private Page currentPage;
    private final List<String> hints = new ArrayList<>();
    private final List<KeyCode> reachedThePage = new ArrayList<>();
    private Stage stage;
    private Button focusedButton;
    private TextField codeField;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException toolkitAlreadyStarted) {
            return;
        }
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void openScene() throws Exception {
        onFx(() -> {
            focusedButton = new Button("botao");
            codeField = new TextField();
            codeField.setId("codeField");
            VBox root = new VBox(focusedButton, codeField);
            root.addEventHandler(KeyEvent.KEY_PRESSED, event -> reachedThePage.add(event.getCode()));
            Scene scene = new Scene(root, 300, 200);
            new ScannerGate(() -> currentPage, hints::add).install(scene);
            stage = new Stage();
            stage.setScene(scene);
            stage.show();
            focusedButton.requestFocus();
        });
    }

    @AfterEach
    void closeScene() throws Exception {
        onFx(() -> stage.close());
    }

    @Test
    void aBurstOnAScreenWithoutScannerNeverReachesThePageAndWarnsOnce() throws Exception {
        currentPage = Page.PAGAMENTO;

        onFx(() -> {
            press(KeyCode.DIGIT7, "7");
            press(KeyCode.DIGIT1, "1");
            press(KeyCode.DIGIT2, "2");
            press(KeyCode.ENTER, "\r");
        });

        assertEquals(List.of(KeyCode.DIGIT7), reachedThePage);
        assertEquals(1, hints.size());
    }

    @Test
    void slowHumanTypingOnAScreenWithoutScannerIsNeverTouched() throws Exception {
        currentPage = Page.PAGAMENTO;

        for (KeyCode digit : List.of(KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3)) {
            onFx(() -> press(digit, "1"));
            Thread.sleep(120);
        }

        assertEquals(3, reachedThePage.size());
        assertTrue(hints.isEmpty());
    }

    @Test
    void aBurstOnTheSaleScreenMovesFocusToTheCodeFieldAndTypesThereInsteadOfOnTheButton() throws Exception {
        currentPage = Page.VENDA;

        onFx(() -> {
            press(KeyCode.DIGIT7, "7");
            press(KeyCode.DIGIT8, "8");
            press(KeyCode.DIGIT9, "9");
        });

        assertSame(codeField, onFxResult(() -> stage.getScene().getFocusOwner()));
        assertTrue(hints.isEmpty());
    }

    @Test
    void shortcutsWithControlOrAltInsideABurstAreSwallowedOnEveryScreen() throws Exception {
        currentPage = Page.VENDA;

        onFx(() -> {
            press(KeyCode.DIGIT7, "7");
            Event.fireEvent(focusedButton, new KeyEvent(KeyEvent.KEY_PRESSED, "v", "v", KeyCode.V,
                    false, true, false, false));
        });

        assertEquals(List.of(KeyCode.DIGIT7), reachedThePage);
    }

    @Test
    void screensWithoutAnyScannerPageStayUntouchedBeforeLogin() throws Exception {
        currentPage = null;

        onFx(() -> {
            press(KeyCode.DIGIT7, "7");
            press(KeyCode.DIGIT1, "1");
            press(KeyCode.DIGIT2, "2");
        });

        assertEquals(3, reachedThePage.size());
    }

    private void press(KeyCode code, String text) {
        Event.fireEvent(stage.getScene().getFocusOwner(),
                new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, false, false, false, false));
    }

    private static void onFx(Runnable action) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                done.complete(null);
            } catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        });
        done.get(20, TimeUnit.SECONDS);
    }

    private static <T> T onFxResult(java.util.function.Supplier<T> supplier) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        Platform.runLater(() -> done.complete(supplier.get()));
        return done.get(20, TimeUnit.SECONDS);
    }
}
