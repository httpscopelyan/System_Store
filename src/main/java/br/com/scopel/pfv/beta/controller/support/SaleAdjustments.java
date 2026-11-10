package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.ElectronicPaymentReceivedException;
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
import java.util.Optional;

public final class SaleAdjustments {

    private static final String NO_SALE_MESSAGE = "Nenhuma venda em andamento";

    private final ActiveSale activeSale;
    private final SaleView view;
    private final SaleService saleService;
    private final CustomerService customerService;
    private final UserSession userSession;
    private final TextField discountField;
    private final TextField customerIdField;

    public SaleAdjustments(ActiveSale activeSale, SaleView view, SaleService saleService,
                           CustomerService customerService, UserSession userSession,
                           TextField discountField, TextField customerIdField) {
        this.activeSale = activeSale;
        this.view = view;
        this.saleService = saleService;
        this.customerService = customerService;
        this.userSession = userSession;
        this.discountField = discountField;
        this.customerIdField = customerIdField;
    }

    public void applyDiscount() {
        if (!requireSale()) {
            return;
        }
        Sale sale = activeSale.current();
        try {
            BigDecimal discount = DecimalInput.parseRequired(discountField.getText());
            Sale updated = saleService.applySaleDiscount(sale, discount);
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
        Optional<String> productCode = view.selectedProductCode();
        if (productCode.isEmpty()) {
            view.showStatus("Selecione na lista o item que deseja cancelar");
            return;
        }
        try {
            Sale updated = saleService.cancelItem(activeSale.current(), productCode.get());
            activeSale.set(updated);
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
        try {
            saleService.cancelSale(activeSale.current());
            view.showStatus("Venda cancelada");
            activeSale.set(saleService.openSale(userSession.getLoggedUser(), userSession.getCaixaAtual()));
            view.showSale(activeSale.current());
            view.showLastItem(null);
        } catch (ElectronicPaymentReceivedException electronicReceived) {
            view.showStatus(electronicReceived.getMessage());
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
