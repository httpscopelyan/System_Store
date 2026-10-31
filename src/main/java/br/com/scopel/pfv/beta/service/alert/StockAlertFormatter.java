package br.com.scopel.pfv.beta.service.alert;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class StockAlertFormatter {

    public String subject(String storeName, int itemCount) {
        return storeName + " — " + itemCount + " item(ns) para repor no estoque";
    }

    public String body(String storeName, List<StockAlert> alerts) {
        StringBuilder text = new StringBuilder();
        text.append(storeName).append(" — Lista de itens que precisam de ser repostos:\n\n");
        if (alerts.isEmpty()) {
            text.append("Nenhum item abaixo do estoque mínimo.\n");
            return text.toString();
        }
        for (StockAlert alert : alerts) {
            text.append(alert.description())
                    .append(": estoque atual ").append(quantity(alert.quantity()))
                    .append(": estoque mínimo ").append(quantity(alert.minStock()))
                    .append(" (repor ").append(quantity(alert.suggestedQuantity())).append(")\n");
        }
        text.append("\n").append(alerts.size()).append(" item(ns) nesta situação.\n");
        return text.toString();
    }

    private String quantity(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
