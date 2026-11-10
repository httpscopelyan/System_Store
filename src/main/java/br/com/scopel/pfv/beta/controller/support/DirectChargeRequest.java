package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.hardware.CardChargeResult;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.DirectChargeService;

import java.math.BigDecimal;

public record DirectChargeRequest(BigDecimal amount, PaymentMethod method, CardType cardType, String note) {

    public String methodLabel() {
        if (method == PaymentMethod.PIX) {
            return "PIX";
        }
        return cardType == CardType.DEBITO ? "Débito" : "Crédito";
    }

    public CardChargeResult createOn(CardPaymentGateway gateway) {
        return method == PaymentMethod.PIX ? gateway.createPixCharge(amount) : gateway.createCharge(amount, cardType);
    }

    public String registerApproved(DirectChargeService directChargeService, User operator, CashRegister cashRegister) {
        String approvedMessage = "✔ Cobrança aprovada: " + MoneyFormat.format(amount) + " (" + methodLabel() + ")";
        try {
            directChargeService.register(amount, method, cardType, operator, cashRegister, note);
            return cashRegister == null
                    ? approvedMessage + " ATENÇÃO: sem caixa aberto, a cobrança não entra no Extra de nenhum caixa."
                    : approvedMessage;
        } catch (RuntimeException failure) {
            return approvedMessage + " ATENÇÃO: não foi possível registrar a cobrança (" + failure.getMessage() + ").";
        }
    }
}
