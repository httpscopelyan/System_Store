package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.hardware.CardChargeResult;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.PaymentInput;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import javafx.scene.input.KeyCode;

import java.math.BigDecimal;

public enum SplitPartMethod {

    CASH("DINHEIRO"),
    DEBIT("DÉBITO"),
    CREDIT("CRÉDITO"),
    PIX("PIX");

    private final String label;

    SplitPartMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isElectronic() {
        return this != CASH;
    }

    public PaymentInput toPaymentInput(BigDecimal amount) {
        return switch (this) {
            case CASH -> PaymentInput.cash(amount);
            case DEBIT -> PaymentInput.card(amount, CardType.DEBITO);
            case CREDIT -> PaymentInput.card(amount, CardType.CREDITO);
            case PIX -> PaymentInput.pix(amount);
        };
    }

    public CardChargeResult createChargeOn(CardPaymentGateway gateway, BigDecimal amount) {
        return switch (this) {
            case DEBIT -> gateway.createCharge(amount, CardType.DEBITO);
            case CREDIT -> gateway.createCharge(amount, CardType.CREDITO);
            case PIX -> gateway.createPixCharge(amount);
            case CASH -> throw new IllegalStateException("Dinheiro não é cobrado na maquininha.");
        };
    }

    public static SplitPartMethod fromKey(KeyCode code) {
        return switch (code) {
            case DIGIT1, NUMPAD1 -> CASH;
            case DIGIT2, NUMPAD2 -> DEBIT;
            case DIGIT3, NUMPAD3 -> CREDIT;
            case DIGIT4, NUMPAD4 -> PIX;
            default -> null;
        };
    }

    public static String labelOf(PaymentMethod method, CardType cardType) {
        if (method == null) {
            return "-";
        }
        return switch (method) {
            case DINHEIRO -> CASH.label;
            case CARTAO -> cardType == CardType.DEBITO ? DEBIT.label : CREDIT.label;
            case PIX -> PIX.label;
            case PRAZO -> "A PRAZO";
        };
    }
}
