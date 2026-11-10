package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.exception.ElectronicPaymentReceivedException;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.SplitPaymentService;

import java.util.Optional;

public final class SplitCashParts {

    private static final String ELECTRONIC_PART_BLOCKS_EXIT =
            "Já existe pagamento recebido no cartão ou PIX; conclua o valor restante.";

    private final SplitPaymentService splitPaymentService;
    private final SplitPaymentView view;

    public SplitCashParts(SplitPaymentService splitPaymentService, SplitPaymentView view) {
        this.splitPaymentService = splitPaymentService;
        this.view = view;
    }

    public String remove(Sale sale, Payment cashPart) {
        try {
            view.show(splitPaymentService.removeCashPart(sale, cashPart.getId()));
            return "Parte em dinheiro removida.";
        } catch (RuntimeException refused) {
            return refused.getMessage();
        }
    }

    public Optional<String> discardAllOrExplainRefusal(Sale sale) {
        if (view.hasElectronicPart()) {
            return Optional.of(ELECTRONIC_PART_BLOCKS_EXIT);
        }
        try {
            while (view.hasCashPart()) {
                view.show(splitPaymentService.removeCashPart(sale, view.lastCashPart().orElseThrow().getId()));
            }
            return Optional.empty();
        } catch (ElectronicPaymentReceivedException electronicReceived) {
            return Optional.of(electronicReceived.getMessage());
        }
    }
}
