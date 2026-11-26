package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.Product;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceByWeightHint {

    private static final String PER_HUNDRED_GRAMS = " (a cada 100 g)";

    private PriceByWeightHint() {
    }

    public static void install(ComboBox<Product.Unidade> unitBox, TextField priceField, Label priceCaption,
                               Label hint) {
        String originalCaption = priceCaption.getText();
        Runnable refresh = () -> {
            boolean perHundredGrams = unitBox.getValue() != null && unitBox.getValue().pricedPerHundredGrams();
            priceCaption.setText(perHundredGrams ? originalCaption + PER_HUNDRED_GRAMS : originalCaption);
            hint.setVisible(perHundredGrams);
            hint.setManaged(perHundredGrams);
            hint.setText(perHundredGrams ? describe(priceField.getText()) : "");
        };
        unitBox.valueProperty().addListener((observable, previous, current) -> refresh.run());
        priceField.textProperty().addListener((observable, previous, current) -> refresh.run());
        refresh.run();
    }

    public static void captionCost(ComboBox<Product.Unidade> unitBox, Label costCaption) {
        String originalCaption = costCaption.getText();
        Runnable refresh = () -> costCaption.setText(
                unitBox.getValue() != null && unitBox.getValue().pricedPerHundredGrams()
                        ? originalCaption + PER_HUNDRED_GRAMS
                        : originalCaption);
        unitBox.valueProperty().addListener((observable, previous, current) -> refresh.run());
        refresh.run();
    }

    static String describe(String priceText) {
        BigDecimal price = DecimalInput.parseOrNull(priceText);
        if (price == null || price.signum() <= 0) {
            return "Informe o preço de 100 g. Na venda, o peso é digitado em gramas.";
        }
        BigDecimal perKilo = Product.Unidade.G100.amountFor(price, BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
        return MoneyFormat.format(price) + " a cada 100 g = " + MoneyFormat.format(perKilo)
                + " o quilo. Estoque continua em KG.";
    }
}
