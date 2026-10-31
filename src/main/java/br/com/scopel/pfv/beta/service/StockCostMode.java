package br.com.scopel.pfv.beta.service;

public enum StockCostMode {

    ULTIMO("Último custo"),
    MEDIA_PONDERADA("Custo médio");

    private final String label;

    StockCostMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static StockCostMode fromSetting(String value) {
        if (value == null) {
            return ULTIMO;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException unknownMode) {
            return ULTIMO;
        }
    }
}
