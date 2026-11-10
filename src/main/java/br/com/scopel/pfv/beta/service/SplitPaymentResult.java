package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.Sale;

import java.math.BigDecimal;

public record SplitPaymentResult(Sale sale, Payment registeredPart, BigDecimal remainingAmount, BigDecimal change,
                                 boolean finalized) {
}
