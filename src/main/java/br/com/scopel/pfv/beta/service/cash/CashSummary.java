package br.com.scopel.pfv.beta.service.cash;

import java.math.BigDecimal;

public record CashSummary(BigDecimal opening, BigDecimal cashIn, BigDecimal pix, BigDecimal credit,
                          BigDecimal debit, BigDecimal totalIn, BigDecimal cashOut, BigDecimal finalBalance) {

    public static CashSummary empty() {
        return new CashSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
