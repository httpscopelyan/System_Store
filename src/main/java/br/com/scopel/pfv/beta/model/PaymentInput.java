package br.com.scopel.pfv.beta.model;

import java.math.BigDecimal;

public record PaymentInput(PaymentMethod method, BigDecimal amount, CardType cardType) {

    public PaymentInput(PaymentMethod method, BigDecimal amount) {
        this(method, amount, null);
    }

    public static PaymentInput cash(BigDecimal amount) {
        return new PaymentInput(PaymentMethod.DINHEIRO, amount, null);
    }

    public static PaymentInput card(BigDecimal amount, CardType cardType) {
        return new PaymentInput(PaymentMethod.CARTAO, amount, cardType);
    }

    public static PaymentInput pix(BigDecimal amount) {
        return new PaymentInput(PaymentMethod.PIX, amount, null);
    }
}
