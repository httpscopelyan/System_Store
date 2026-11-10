package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.exception.InsufficientCashException;
import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.cash.CashMovementRow;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

public final class CashMovementActions {

    public record Result(boolean success, String message) {
    }

    private final CashRegisterService cashRegisterService;
    private final ManagerAuthorizedAction managerAction;
    private final Supplier<Window> ownerWindow;

    public CashMovementActions(CashRegisterService cashRegisterService, ManagerAuthorizedAction managerAction,
                               Supplier<Window> ownerWindow) {
        this.cashRegisterService = cashRegisterService;
        this.managerAction = managerAction;
        this.ownerWindow = ownerWindow;
    }

    public Result register(CashRegister open, boolean supplement, String amountText, String reason) {
        if (open == null) {
            return new Result(false, "Abra um caixa primeiro.");
        }
        try {
            BigDecimal amount = DecimalInput.parseRequired(amountText);
            if (supplement) {
                cashRegisterService.supplement(open.getId(), amount, reason);
            } else {
                cashRegisterService.withdrawal(open.getId(), amount, reason);
            }
            return new Result(true, supplement ? "Suprimento registrado!" : "Sangria registrada!");
        } catch (NumberFormatException invalidNumber) {
            return new Result(false, "Valor inválido.");
        } catch (InsufficientCashException insufficient) {
            return new Result(false, "Saldo insuficiente para a sangria.");
        } catch (Exception refused) {
            return new Result(false, "Não foi possível: " + refused.getMessage());
        }
    }

    public Result rectify(CashMovementRow row) {
        if (row != null && row.isDirectCharge()) {
            return new Result(false, "Cobrança da aba Cobrar não pode ser retificada.");
        }
        if (row == null || !row.isRectifiable()) {
            return new Result(false, "Selecione uma sangria ou suprimento na lista para retificar.");
        }
        String description = row.movement().getType().name() + " de " + MoneyFormat.format(row.movement().getAmount());
        Optional<RectificationDialog.Input> input = RectificationDialog.ask(ownerWindow.get(), description);
        if (input.isEmpty()) {
            return new Result(false, "");
        }
        try {
            CashMovement rectification = managerAction.apply(actor -> cashRegisterService.rectify(
                    row.movement().getId(), input.get().correctedAmount(), input.get().reason(), actor));
            return rectification == null
                    ? new Result(false, "Retificação não autorizada.")
                    : new Result(true, "Retificação registrada!");
        } catch (RuntimeException refused) {
            return new Result(false, "Não foi possível retificar: " + refused.getMessage());
        }
    }
}
