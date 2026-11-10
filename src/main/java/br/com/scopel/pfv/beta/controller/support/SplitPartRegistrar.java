package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SplitPaymentResult;
import br.com.scopel.pfv.beta.service.SplitPaymentService;

import java.math.BigDecimal;

public final class SplitPartRegistrar {

    public record Registration(Sale sale, SplitStep nextStep, String message) {
    }

    private final SplitPaymentService splitPaymentService;
    private final UserSession userSession;
    private final SplitPaymentView view;
    private final SplitPaymentScreen screen;
    private final SplitSaleCompletion completion;

    public SplitPartRegistrar(SplitPaymentService splitPaymentService, ReceiptService receiptService,
                              UserSession userSession, Navigator navigator, SplitPaymentView view,
                              SplitPaymentScreen screen) {
        this.splitPaymentService = splitPaymentService;
        this.userSession = userSession;
        this.view = view;
        this.screen = screen;
        this.completion = new SplitSaleCompletion(splitPaymentService, receiptService, userSession, navigator);
    }

    public Registration register(Sale sale, SplitPartMethod method, BigDecimal amount) {
        try {
            SplitPaymentResult result = splitPaymentService.registerPart(sale, method.toPaymentInput(amount));
            userSession.setVendaAtual(result.sale());
            screen.hideFailure();
            if (result.finalized()) {
                completion.finish(result);
                return new Registration(result.sale(), null, "");
            }
            view.show(splitPaymentService.status(result.sale()));
            return new Registration(result.sale(), SplitStep.CHOOSING_METHOD,
                    "Parte registrada. Falta pagar " + MoneyFormat.format(result.remainingAmount()) + ".");
        } catch (RuntimeException failure) {
            if (method.isElectronic()) {
                screen.showRegistrationFailure(failure.getMessage());
                return new Registration(sale, SplitStep.REGISTRATION_FAILED, "");
            }
            return new Registration(sale, SplitStep.ENTERING_AMOUNT, failure.getMessage());
        }
    }
}
