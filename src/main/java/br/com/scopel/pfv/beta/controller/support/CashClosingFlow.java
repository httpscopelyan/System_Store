package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ReceiptService;

public final class CashClosingFlow {

    private final CashRegisterService cashRegisterService;
    private final ReceiptService receiptService;
    private final UserSession userSession;

    public CashClosingFlow(CashRegisterService cashRegisterService, ReceiptService receiptService,
                           UserSession userSession) {
        this.cashRegisterService = cashRegisterService;
        this.receiptService = receiptService;
        this.userSession = userSession;
    }

    public String close(CashRegister open, String countedText) {
        if (open == null) {
            return "Não há caixa aberto para fechar.";
        }
        if (countedText == null || countedText.isBlank()) {
            return "Informe o valor contado para fechar o caixa.";
        }
        try {
            CashRegister closed = cashRegisterService.close(open.getId(), DecimalInput.parseRequired(countedText));
            userSession.setCaixaAtual(null);
            return "Caixa FECHADO!  Esperado: " + MoneyFormat.format(closed.getExpectedAmount())
                    + "  |  Contado: " + MoneyFormat.format(closed.getClosingAmount())
                    + "  |  Diferença: " + MoneyFormat.format(closed.getDifference()) + printSuffix(closed);
        } catch (NumberFormatException invalidNumber) {
            return "Valor contado inválido.";
        } catch (Exception failure) {
            return "Não foi possível fechar: " + failure.getMessage();
        }
    }

    public String printClosing(CashRegister register) {
        if (register == null) {
            return "Nenhum caixa para imprimir o fechamento.";
        }
        if (register.getStatus() != CashRegister.Status.CLOSED) {
            return "Feche o caixa antes de imprimir o fechamento.";
        }
        try {
            receiptService.printCashCloseReceipt(register, cashRegisterService.movements(register));
            return "Fechamento enviado para impressão.";
        } catch (Exception failure) {
            return "Não foi possível imprimir: " + failure.getMessage();
        }
    }

    private String printSuffix(CashRegister closed) {
        try {
            receiptService.printCashCloseReceipt(closed, cashRegisterService.movements(closed));
            return "";
        } catch (Exception printFailure) {
            return "  |  Aviso: impressão falhou (" + printFailure.getMessage() + ")";
        }
    }
}
