package br.com.scopel.pfv.beta.controller.support;

import java.math.BigDecimal;

public final class DecimalInput {

    private DecimalInput() {
    }

    public static BigDecimal parseOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim().replace(',', '.'));
        } catch (NumberFormatException invalidNumber) {
            return null;
        }
    }

    public static BigDecimal parseOrZero(String text) {
        BigDecimal parsed = parseOrNull(text);
        return parsed == null ? BigDecimal.ZERO : parsed;
    }

    public static BigDecimal parseRequired(String text) {
        BigDecimal parsed = parseOrNull(text);
        if (parsed == null) {
            throw new NumberFormatException("Valor numérico inválido: " + text);
        }
        return parsed;
    }
}
