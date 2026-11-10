package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.Payment;

import java.math.BigDecimal;
import java.util.List;

public record SplitPaymentStatus(BigDecimal total, BigDecimal paidAmount, BigDecimal remainingAmount,
                                 List<Payment> parts) {

    public boolean hasParts() {
        return !parts.isEmpty();
    }
}
