package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.service.PricingSuggestionService;
import javafx.scene.control.TextField;

import java.math.BigDecimal;

public final class SuggestedPriceBinding {

    private static final String SUGGESTED_STYLE_CLASS = "suggested-value";

    private final TextField costField;
    private final TextField marginField;
    private final TextField priceField;
    private final PricingSuggestionService pricingSuggestionService;
    private final boolean useDefaultMarginWhenBlank;

    private boolean priceIsSuggestion;
    private boolean writingSuggestion;

    public SuggestedPriceBinding(TextField costField, TextField marginField, TextField priceField,
                                 PricingSuggestionService pricingSuggestionService,
                                 boolean useDefaultMarginWhenBlank) {
        this.costField = costField;
        this.marginField = marginField;
        this.priceField = priceField;
        this.pricingSuggestionService = pricingSuggestionService;
        this.useDefaultMarginWhenBlank = useDefaultMarginWhenBlank;
        this.priceIsSuggestion = useDefaultMarginWhenBlank;
        install();
    }

    public void loadPrice(String priceText) {
        priceIsSuggestion = false;
        write(priceText);
    }

    public void reset() {
        priceIsSuggestion = useDefaultMarginWhenBlank;
        write("");
    }

    private void install() {
        costField.textProperty().addListener((observable, previous, current) -> onCostChanged());
        marginField.textProperty().addListener((observable, previous, current) -> onMarginChanged());
        priceField.textProperty().addListener((observable, previous, current) -> onPriceChanged(current));
        priceField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused && priceField.getText().isBlank()) {
                priceIsSuggestion = true;
                recompute();
            }
        });
    }

    private void onCostChanged() {
        if (priceIsSuggestion) {
            recompute();
        }
    }

    private void onMarginChanged() {
        priceIsSuggestion = true;
        recompute();
    }

    private void onPriceChanged(String currentText) {
        if (writingSuggestion) {
            return;
        }
        priceIsSuggestion = currentText == null || currentText.isBlank();
        refreshStyle();
    }

    private void recompute() {
        BigDecimal cost = DecimalInput.parseOrNull(costField.getText());
        BigDecimal margin = marginField.getText().isBlank() && useDefaultMarginWhenBlank
                ? pricingSuggestionService.defaultMarginPercent()
                : DecimalInput.parseOrNull(marginField.getText());
        BigDecimal suggestion = pricingSuggestionService.suggestFor(cost, margin);
        if (suggestion != null) {
            write(suggestion.toPlainString());
        } else if (useDefaultMarginWhenBlank) {
            write("");
        }
    }

    private void write(String text) {
        writingSuggestion = true;
        try {
            priceField.setText(text);
        } finally {
            writingSuggestion = false;
        }
        refreshStyle();
    }

    private void refreshStyle() {
        boolean showAsSuggestion = priceIsSuggestion && !priceField.getText().isBlank();
        if (showAsSuggestion) {
            if (!priceField.getStyleClass().contains(SUGGESTED_STYLE_CLASS)) {
                priceField.getStyleClass().add(SUGGESTED_STYLE_CLASS);
            }
        } else {
            priceField.getStyleClass().remove(SUGGESTED_STYLE_CLASS);
        }
    }
}
