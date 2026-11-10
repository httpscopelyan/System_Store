package br.com.scopel.pfv.beta.controller.support;

import java.math.BigDecimal;

public record SplitAmountDecision(Action action, BigDecimal amount, String message) {

    public enum Action { REJECT, REGISTER_NOW, START_MACHINE_CHARGE, CONFIRM_MANUALLY }

    public static SplitAmountDecision of(String typedAmount, SplitPartMethod method, BigDecimal remainingAmount,
                                         boolean machineIntegrated) {
        BigDecimal amount = DecimalInput.parseOrNull(typedAmount);
        if (amount == null || amount.signum() <= 0) {
            return reject("Informe o valor desta parte.");
        }
        if (!method.isElectronic()) {
            return new SplitAmountDecision(Action.REGISTER_NOW, amount, "");
        }
        if (amount.compareTo(remainingAmount) > 0) {
            return reject("Cartão ou PIX não pode passar do que falta (" + MoneyFormat.format(remainingAmount)
                    + "). Troco só sai de pagamento em dinheiro.");
        }
        return new SplitAmountDecision(machineIntegrated ? Action.START_MACHINE_CHARGE : Action.CONFIRM_MANUALLY,
                amount, "");
    }

    private static SplitAmountDecision reject(String message) {
        return new SplitAmountDecision(Action.REJECT, null, message);
    }
}
