package br.com.scopel.pfv.beta.service.cash;

import br.com.scopel.pfv.beta.model.CashMovement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashMovementRow(LocalDateTime at, boolean entry, BigDecimal cash, BigDecimal pix,
                              BigDecimal credit, BigDecimal debit, BigDecimal extra, BigDecimal total,
                              Long saleId, String history, String operatorName, CashMovement movement) {

    public boolean isRectifiable() {
        return movement != null && movement.getType() != CashMovement.Type.RETIFICACAO;
    }

    public boolean isDirectCharge() {
        return extra != null && extra.signum() > 0;
    }
}
