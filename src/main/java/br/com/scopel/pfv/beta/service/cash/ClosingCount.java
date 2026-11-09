package br.com.scopel.pfv.beta.service.cash;

import java.math.BigDecimal;

public record ClosingCount(BigDecimal cash, BigDecimal pix, BigDecimal credit, BigDecimal debit) {
}
