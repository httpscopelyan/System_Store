package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.refund.RefundPlan;
import br.com.scopel.pfv.beta.service.refund.SaleRefundService;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.Supplier;

public final class SaleRefundAction {

    public static final String NO_SALE_SELECTED_MESSAGE = "Selecione uma venda para reembolsar.";
    public static final String NOT_A_SALE_MESSAGE =
            "Esta linha não é uma venda; selecione uma venda para reembolsar.";
    public static final String CANCELED_MESSAGE = "Reembolso cancelado.";

    private final SaleRefundService refundService;
    private final ManagerAuthorizedAction managerAction;
    private final Supplier<Window> ownerWindow;

    public SaleRefundAction(SaleRefundService refundService, ManagerAuthorizedAction managerAction,
                            Supplier<Window> ownerWindow) {
        this.refundService = refundService;
        this.managerAction = managerAction;
        this.ownerWindow = ownerWindow;
    }

    public Result refund(Long saleId, boolean rowWasSelected) {
        if (!rowWasSelected) {
            return new Result(false, NO_SALE_SELECTED_MESSAGE);
        }
        if (saleId == null) {
            return new Result(false, NOT_A_SALE_MESSAGE);
        }
        try {
            RefundPlan plan = refundService.plan(saleId);
            Optional<SaleRefundDialog.Input> input = SaleRefundDialog.ask(ownerWindow.get(), plan);
            if (input.isEmpty()) {
                return new Result(false, CANCELED_MESSAGE);
            }
            Sale refunded = managerAction.apply(manager -> refundService.refund(saleId, input.get().reason(),
                    manager, input.get().manualRefundConfirmed()));
            if (refunded == null) {
                return new Result(false, CANCELED_MESSAGE);
            }
            return new Result(true, successMessage(plan));
        } catch (RuntimeException failure) {
            return new Result(false, failure.getMessage() == null ? "Não foi possível reembolsar a venda."
                    : failure.getMessage());
        }
    }

    private String successMessage(RefundPlan plan) {
        String message = "Venda #" + plan.saleId() + " reembolsada.";
        if (plan.cashToReturn().signum() > 0) {
            message += " Devolva " + MoneyFormat.format(plan.cashToReturn()) + " em dinheiro ao cliente.";
        }
        return message;
    }

    public record Result(boolean success, String message) {
    }
}
