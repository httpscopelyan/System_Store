package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.cash.CashClosingService;
import br.com.scopel.pfv.beta.service.cash.CashOverview;
import br.com.scopel.pfv.beta.service.cash.ClosingCount;

public final class CashClosingFlow {

    public static final String CLOSED_PREFIX = "Caixa FECHADO";

    private final CashClosingService closingService;
    private final CashRegisterService cashRegisterService;
    private final ReceiptService receiptService;
    private final UserSession userSession;

    public CashClosingFlow(CashClosingService closingService, CashRegisterService cashRegisterService,
                           ReceiptService receiptService, UserSession userSession) {
        this.closingService = closingService;
        this.cashRegisterService = cashRegisterService;
        this.receiptService = receiptService;
        this.userSession = userSession;
    }

    public String close(User operator, ClosingCount counted) {
        CashRegister open = operator == null ? null : cashRegisterService.findOpenByOperator(operator).orElse(null);
        if (open == null) {
            return "Não há caixa aberto para fechar.";
        }
        try {
            CashRegister closed = closingService.close(open.getId(), counted);
            userSession.setCaixaAtual(null);
            return CLOSED_PREFIX + "!  Diferença total: " + MoneyFormat.format(closed.totalDifference())
                    + printSuffix(closed);
        } catch (Exception failure) {
            return "Não foi possível fechar: " + failure.getMessage();
        }
    }

    public String printMovements(CashRegister register, CashOverview overview) {
        if (register == null) {
            return "Nenhum caixa para imprimir a movimentação.";
        }
        try {
            receiptService.printCashMovementsReceipt(register, overview);
            return "Movimentação enviada para impressão.";
        } catch (Exception failure) {
            return "Não foi possível imprimir: " + failure.getMessage();
        }
    }

    private String printSuffix(CashRegister closed) {
        try {
            receiptService.printCashCloseReceipt(closed, cashRegisterService.movements(closed));
            return "  |  Fechamento impresso.";
        } catch (Exception printFailure) {
            return "  |  Aviso: impressão falhou (" + printFailure.getMessage() + ")";
        }
    }
}
