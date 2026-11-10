package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.ReceiptPayment;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SplitPaymentResult;
import br.com.scopel.pfv.beta.service.SplitPaymentService;

import java.util.List;

public final class SplitSaleCompletion {

    private final SplitPaymentService splitPaymentService;
    private final ReceiptService receiptService;
    private final UserSession userSession;
    private final Navigator navigator;

    public SplitSaleCompletion(SplitPaymentService splitPaymentService, ReceiptService receiptService,
                               UserSession userSession, Navigator navigator) {
        this.splitPaymentService = splitPaymentService;
        this.receiptService = receiptService;
        this.userSession = userSession;
        this.navigator = navigator;
    }

    public void finish(SplitPaymentResult result) {
        Sale finalizedSale = result.sale();
        List<ReceiptPayment> receiptPayments = splitPaymentService.receiptPayments(finalizedSale);
        BackgroundPrinting.run(() -> receiptService.printSplitSaleReceipt(finalizedSale, receiptPayments),
                message -> navigator.notice("Venda gravada. Aviso: impressão falhou (" + message + ")"));
        userSession.setUltimaVenda(finalizedSale);
        userSession.setUltimaForma(null);
        userSession.setUltimoTroco(result.change());
        userSession.setVendaAtual(null);
        navigator.navigate(Page.VENDA_FINALIZADA);
    }
}
