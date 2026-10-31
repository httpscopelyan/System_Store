package br.com.scopel.pfv.beta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CostAveraging {

    private CostAveraging() {
    }

    public static BigDecimal weightedAverageCost(BigDecimal currentStock, BigDecimal currentCost,
                                                 BigDecimal entryQuantity, BigDecimal entryCost) {
        boolean noReliableHistory = currentStock == null || currentStock.signum() <= 0
                || currentCost == null || currentCost.signum() <= 0;
        if (noReliableHistory) {
            return entryCost.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal totalCost = currentStock.multiply(currentCost).add(entryQuantity.multiply(entryCost));
        BigDecimal totalQuantity = currentStock.add(entryQuantity);
        return totalCost.divide(totalQuantity, 2, RoundingMode.HALF_UP);
    }
}
