package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.Product;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.util.regex.Pattern;

public final class QuantityInput {

    private static final Pattern UNIT_PATTERN = Pattern.compile("\\d{0,6}");
    private static final Pattern WEIGHT_PATTERN = Pattern.compile("\\d{0,6}([.,]\\d{0,3})?");

    private QuantityInput() {
    }

    public static void configure(TextField field, Product.Unidade unit) {
        Pattern allowed = unit == Product.Unidade.KG ? WEIGHT_PATTERN : UNIT_PATTERN;
        field.setTextFormatter(null);
        field.clear();
        field.setTextFormatter(new TextFormatter<String>(change ->
                allowed.matcher(change.getControlNewText()).matches() ? change : null));
    }

    public static boolean accepts(Product.Unidade unit, String text) {
        Pattern allowed = unit == Product.Unidade.KG ? WEIGHT_PATTERN : UNIT_PATTERN;
        return allowed.matcher(text).matches();
    }
}
