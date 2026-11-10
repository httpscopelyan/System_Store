package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.service.SaleHistoryEntry;
import br.com.scopel.pfv.beta.service.SaleHistoryService;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.Supplier;

public final class SaleDetailAction {

    public static final String NO_SALE_SELECTED_MESSAGE = "Selecione uma venda para ver os itens.";
    public static final String NOT_A_SALE_MESSAGE =
            "Esta linha não é uma venda; selecione uma venda para ver os itens.";
    public static final String SALE_NOT_FOUND_MESSAGE = "Venda não encontrada.";

    private final SaleHistoryService saleHistoryService;
    private final Supplier<Window> ownerWindow;

    public SaleDetailAction(SaleHistoryService saleHistoryService, Supplier<Window> ownerWindow) {
        this.saleHistoryService = saleHistoryService;
        this.ownerWindow = ownerWindow;
    }

    public String showItemsOf(Long saleId, boolean rowWasSelected) {
        if (!rowWasSelected) {
            return NO_SALE_SELECTED_MESSAGE;
        }
        if (saleId == null) {
            return NOT_A_SALE_MESSAGE;
        }
        Optional<SaleHistoryEntry> saleDetail = saleHistoryService.findSaleDetail(saleId);
        if (saleDetail.isEmpty()) {
            return SALE_NOT_FOUND_MESSAGE;
        }
        SaleDetailDialog.show(ownerWindow.get(), saleDetail.get());
        return "";
    }
}
