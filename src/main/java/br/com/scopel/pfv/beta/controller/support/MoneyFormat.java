package br.com.scopel.pfv.beta.controller.support;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class MoneyFormat {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private MoneyFormat() {
    }

    public static String format(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(PT_BR));
        return "R$ " + format.format(value == null ? BigDecimal.ZERO : value);
    }

    public static String plain(BigDecimal value) {
        BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
        return safeValue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public static String quantity(BigDecimal value) {
        if (value == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("#,##0.###", new DecimalFormatSymbols(PT_BR));
        return format.format(value);
    }
}
