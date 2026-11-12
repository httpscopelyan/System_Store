package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;

public final class DirectChargeGuard {

    private DirectChargeGuard() {
    }

    public static String problem(AmountInput input, CardPaymentGateway gateway) {
        if (!input.isPositive()) {
            return "Digite o valor a cobrar.";
        }
        if (!gateway.isIntegrated()) {
            return "A maquininha não está no modo PDV. Mude o modo em Configurações → Maquininha.";
        }
        return null;
    }
}
