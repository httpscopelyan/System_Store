package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InvalidDiscountException;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.exception.SaleItemNotFoundException;
import br.com.scopel.pfv.beta.model.Customer;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.CustomerService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.scene.control.TextField;

import java.math.BigDecimal;

public final class SaleAdjustments {

    private static final String NO_SALE_MESSAGE = "Nenhuma venda em andamento";

    private final ActiveSale activeSale;
    private final SaleView view;
    private final SaleService saleService;
    private final CustomerService customerService;
    private final UserSession userSession;
    private final ManagerAuthorizedAction managerAction;
    private final TextField discountField;
    private final TextField customerIdField;
    private final TextField cancelItemCodeField;
    private final TextField cancelReasonField;

    public SaleAdjustments(ActiveSale activeSale, SaleView view, SaleService saleService,
                           CustomerService customerService, UserSession userSession,
                           ManagerAuthorizedAction managerAction, TextField discountField,
                           TextField customerIdField, TextField cancelItemCodeField,
                           TextField cancelReasonField) {
        this.activeSale = activeSale;
        this.view = view;
        this.saleService = saleService;
        this.customerService = customerService;
        this.userSession = userSession;
        this.managerAction = managerAction;
        this.discountField = discountField;
        this.customerIdField = customerIdField;
        this.cancelItemCodeField = cancelItemCodeField;
        this.cancelReasonField = cancelReasonField;
    }

    public void applyDiscount() {
        if (!requireSale()) {
            return;
        }
        Sale sale = activeSale.current();
        try {
            BigDecimal discount = DecimalInput.parseRequired(discountField.getText());
            Sale updated = managerAction.apply(authorizedBy -> saleService.applySaleDiscount(sale, discount, authorizedBy));
            if (updated == null) {
                view.showStatus("Desconto acima do limite: não autorizado");
                return;
            }
            activeSale.set(updated);
            view.showSale(updated);
            discountField.clear();
            view.showStatus("Desconto aplicado");
        } catch (NumberFormatException invalidNumber) {
            view.showStatus("Desconto inválido");
        } catch (InvalidDiscountException invalidDiscount) {
            view.showStatus(invalidDiscount.getMessage());
        }
    }

    public void linkCustomer() {
        if (!requireSale()) {
            return;
        }
        try {
            Customer customer = customerService.findById(Long.parseLong(customerIdField.getText().trim()));
            activeSale.set(saleService.setCustomer(activeSale.current(), customer));
            customerIdField.clear();
            view.showStatus("Cliente: " + customer.getName());
        } catch (NumberFormatException invalidId) {
            view.showStatus("ID de cliente inválido");
        } catch (Exception customerNotFound) {
            view.showStatus("Cliente não encontrado");
        }
    }

    public void cancelItem() {
        if (!requireSale()) {
            return;
        }
        Sale sale = activeSale.current();
        String productCode = cancelItemCodeField.getText().trim();
        try {
            Sale updated = managerAction.apply(authorizedBy -> saleService.cancelItem(sale, productCode, authorizedBy));
            if (updated == null) {
                view.showStatus("Cancelamento de item não autorizado");
                return;
            }
            activeSale.set(updated);
            cancelItemCodeField.clear();
            view.showSale(updated);
            view.showLastItem(view.lastItemOf(updated));
            view.showStatus("Item cancelado");
        } catch (SaleItemNotFoundException | ProductNotfindException itemNotInSale) {
            view.showStatus("Item não está na venda");
        }
    }

    public void cancelSale() {
        if (!requireSale()) {
            return;
        }
        Sale sale = activeSale.current();
        String reason = cancelReasonField.getText();
        try {
            Sale canceled = managerAction.apply(authorizedBy -> saleService.cancelSale(sale, reason, authorizedBy));
            if (canceled == null) {
                view.showStatus("Cancelamento de venda não autorizado");
                return;
            }
            cancelReasonField.clear();
            view.showStatus("Venda cancelada");
            activeSale.set(saleService.openSale(userSession.getLoggedUser(), userSession.getCaixaAtual()));
            view.showSale(activeSale.current());
            view.showLastItem(null);
        } catch (IllegalSaleStateException invalidState) {
            view.showStatus("Não é possível cancelar nesta situação");
        }
    }

    private boolean requireSale() {
        if (activeSale.exists()) {
            return true;
        }
        view.showStatus(NO_SALE_MESSAGE);
        return false;
    }
}
