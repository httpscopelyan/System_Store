package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.exception.InvalidQuantityException;

import java.math.BigDecimal;

public final class ScanInputParser {

    public record ParsedScan(String code, BigDecimal quantity) {

        public boolean hasQuantity() {
            return quantity != null;
        }
    }

    private ScanInputParser() {
    }

    public static ParsedScan parse(String rawInput) {
        String trimmedInput = rawInput == null ? "" : rawInput.trim();
        int separatorIndex = trimmedInput.indexOf('*');

        if (separatorIndex < 0) {
            return new ParsedScan(trimmedInput, null);
        }
        if (trimmedInput.indexOf('*', separatorIndex + 1) >= 0) {
            throw new InvalidQuantityException("Formato inválido: use quantidade*código");
        }

        String quantityText = trimmedInput.substring(0, separatorIndex).trim();
        String code = trimmedInput.substring(separatorIndex + 1).trim();

        if (quantityText.isEmpty()) {
            throw new InvalidQuantityException("Informe a quantidade antes do *");
        }
        if (code.isEmpty()) {
            throw new InvalidQuantityException("Informe o código do produto");
        }
        BigDecimal quantity = DecimalInput.parseOrNull(quantityText);
        if (quantity == null) {
            throw new InvalidQuantityException("Quantidade inválida");
        }
        return new ParsedScan(code, quantity);
    }
}
