package br.com.scopel.pfv.beta.service.refund;

import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

public record RefundPlan(Long saleId, BigDecimal total, BigDecimal cashToReturn, List<ElectronicPart> electronicParts) {

    public record ElectronicPart(Long paymentId, PaymentMethod method, CardType cardType, BigDecimal amount,
                                 boolean automatic, boolean alreadyRefunded) {
    }

    public boolean needsManualConfirmation() {
        return electronicParts.stream().anyMatch(part -> !part.automatic() && !part.alreadyRefunded());
    }
}
