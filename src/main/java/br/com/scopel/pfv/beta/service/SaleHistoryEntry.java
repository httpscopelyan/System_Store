package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SaleHistoryEntry(Long saleId, LocalDateTime createdAt, Long operatorId, String operatorName,
                               String customerName, BigDecimal discount, BigDecimal surcharge, BigDecimal total,
                               List<Item> items, List<PaymentPart> payments) {

    public record Item(String productCode, String productDescription, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal discount, BigDecimal subtotal) {
    }

    public record PaymentPart(PaymentMethod method, CardType cardType, BigDecimal amountReceived,
                              BigDecimal change, BigDecimal netAmount) {
    }
}
