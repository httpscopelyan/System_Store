package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.controller.support.SuggestedPriceBinding;
import br.com.scopel.pfv.beta.service.PricingSuggestionService;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class SuggestedPriceBindingTests {

    private static final String SUGGESTED = "suggested-value";

    @Autowired
    private PricingSuggestionService pricingSuggestionService;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        FxTestSupport.startToolkit();
    }

    private final TextField cost = new TextField();
    private final TextField margin = new TextField();
    private final TextField price = new TextField();

    private SuggestedPriceBinding createBinding(boolean useDefaultMargin) {
        return new SuggestedPriceBinding(cost, margin, price, pricingSuggestionService, useDefaultMargin);
    }

    @Test
    void costAloneSuggestsThePriceWithTheDefaultMarginAndMarksItAsSuggestion_RN27() throws Exception {
        FxTestSupport.runOnFx(() -> {
            createBinding(true);
            cost.setText("10.00");
            assertEquals("13.00", price.getText());
            assertTrue(price.getStyleClass().contains(SUGGESTED));
        });
    }

    @Test
    void costAndMarginSuggestThePrice_RN27() throws Exception {
        FxTestSupport.runOnFx(() -> {
            createBinding(true);
            cost.setText("0.80");
            margin.setText("80");
            assertEquals("1.44", price.getText());
        });
    }

    @Test
    void typedPriceWinsAndIsNeverOverwrittenByCostChanges_RN27() throws Exception {
        FxTestSupport.runOnFx(() -> {
            createBinding(true);
            cost.setText("10.00");
            price.setText("15.50");
            cost.setText("20.00");
            assertEquals("15.50", price.getText());
            assertFalse(price.getStyleClass().contains(SUGGESTED));
        });
    }

    @Test
    void clearingTheTypedPriceBringsTheSuggestionBackOnNextCostChange_RN27() throws Exception {
        FxTestSupport.runOnFx(() -> {
            createBinding(true);
            cost.setText("10.00");
            price.setText("15.50");
            price.setText("");
            cost.setText("20.00");
            assertEquals("26.00", price.getText());
            assertTrue(price.getStyleClass().contains(SUGGESTED));
        });
    }

    @Test
    void editingTheMarginAskForANewSuggestionEvenAfterATypedPrice_RN27() throws Exception {
        FxTestSupport.runOnFx(() -> {
            createBinding(true);
            cost.setText("10.00");
            price.setText("15.50");
            margin.setText("50");
            assertEquals("15.00", price.getText());
            assertTrue(price.getStyleClass().contains(SUGGESTED));
        });
    }

    @Test
    void clearingTheCostClearsTheSuggestionOnTheCreateForm_RN27() throws Exception {
        FxTestSupport.runOnFx(() -> {
            createBinding(true);
            cost.setText("10.00");
            cost.setText("");
            assertEquals("", price.getText());
            assertFalse(price.getStyleClass().contains(SUGGESTED));
        });
    }

    @Test
    void editFormKeepsTheStoredPriceUntilTheMarginIsTyped_RN27() throws Exception {
        FxTestSupport.runOnFx(() -> {
            SuggestedPriceBinding binding = createBinding(false);
            cost.setText("10.00");
            binding.loadPrice("12.00");
            cost.setText("11.00");
            assertEquals("12.00", price.getText());
            assertFalse(price.getStyleClass().contains(SUGGESTED));
            margin.setText("100");
            assertEquals("22.00", price.getText());
            assertTrue(price.getStyleClass().contains(SUGGESTED));
        });
    }

    @Test
    void resetReturnsTheFormToSuggestionMode_RN27() throws Exception {
        FxTestSupport.runOnFx(() -> {
            SuggestedPriceBinding binding = createBinding(true);
            cost.setText("10.00");
            price.setText("15.50");
            binding.reset();
            cost.setText("");
            cost.setText("2.00");
            assertEquals("2.60", price.getText());
        });
    }
}
