package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentMethod;

import java.math.BigDecimal;

public record ReceiptPayment(PaymentMethod method, CardType cardType, BigDecimal received, BigDecimal change) {

    public static ReceiptPayment cash(BigDecimal received, BigDecimal change) {
        return new ReceiptPayment(PaymentMethod.DINHEIRO, null, received, change);
    }

    public static ReceiptPayment card(CardType cardType) {
        return new ReceiptPayment(PaymentMethod.CARTAO, cardType, null, null);
    }

    public static ReceiptPayment pix() {
        return new ReceiptPayment(PaymentMethod.PIX, null, null, null);
    }

    public static ReceiptPayment of(Payment payment) {
        return new ReceiptPayment(payment.getMethod(), payment.getCardType(), payment.getAmountReceived(),
                payment.getChange());
    }
}
