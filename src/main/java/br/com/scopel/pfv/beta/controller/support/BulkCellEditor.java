package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.controller.support.ProductTablePresenter.CellEdit;
import br.com.scopel.pfv.beta.controller.support.ProductTablePresenter.EditableColumn;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.ProductBulkEditService;
import br.com.scopel.pfv.beta.service.ProductBulkEditService.BulkResult;
import javafx.scene.control.TableColumn;

import java.math.BigDecimal;
import java.util.List;

public final class BulkCellEditor {

    @FunctionalInterface
    public interface Confirmation {
        boolean confirm(String message);
    }

    public record Outcome(boolean applied, String message, List<Long> productIds,
                          TableColumn<Product, ?> tableColumn) {
    }

    private final ProductBulkEditService bulkEditService;
    private final ProductSelection selection;
    private final ManagerAuthorizedAction managerAction;
    private final Confirmation confirmation;

    public BulkCellEditor(ProductBulkEditService bulkEditService, ProductSelection selection,
                          ManagerAuthorizedAction managerAction, Confirmation confirmation) {
        this.bulkEditService = bulkEditService;
        this.selection = selection;
        this.managerAction = managerAction;
        this.confirmation = confirmation;
    }

    public Outcome commit(CellEdit edit) {
        List<Long> ids = targetIds(edit);
        BigDecimal number = null;
        if (edit.column() != EditableColumn.NAME && edit.column() != EditableColumn.CODE) {
            number = parseNumber(edit.text());
            if (number == null) {
                return rejected("Valor inválido: " + edit.text(), ids, edit);
            }
        }
        if (ids.size() > 1 && !confirmation.confirm(confirmationText(edit.column(), edit.text(), number, ids.size()))) {
            return rejected("Alteração cancelada.", ids, edit);
        }
        try {
            BulkResult result = apply(edit, number, ids);
            if (result == null) {
                return rejected("Alteração não autorizada.", ids, edit);
            }
            return new Outcome(true, summary(result), ids, edit.tableColumn());
        } catch (IllegalArgumentException invalidValue) {
            return rejected(invalidValue.getMessage(), ids, edit);
        } catch (ManagerAuthorizationRequiredException notAllowed) {
            return rejected("Seu usuário não tem permissão para esta alteração.", ids, edit);
        }
    }

    public Outcome acceptSuggestion(List<Long> ids, TableColumn<Product, ?> column) {
        if (ids.isEmpty()) {
            return new Outcome(false, "Selecione células da coluna Vr. Venda.", ids, column);
        }
        try {
            BulkResult result = managerAction.apply(actor -> bulkEditService.applySuggestedPrice(ids, actor));
            if (result == null) {
                return new Outcome(false, "Alteração não autorizada.", ids, column);
            }
            String skipped = result.skipped() > 0 ? " (" + result.skipped() + " sem custo)" : "";
            return new Outcome(true, "Sugestão aplicada em " + result.changed() + " de " + ids.size() + skipped,
                    ids, column);
        } catch (IllegalArgumentException invalidValue) {
            return new Outcome(false, invalidValue.getMessage(), ids, column);
        }
    }

    private List<Long> targetIds(CellEdit edit) {
        if (edit.column() == EditableColumn.NAME || edit.column() == EditableColumn.CODE
                || edit.column() == EditableColumn.STOCK) {
            return List.of(edit.product().getId());
        }
        List<Long> selected = selection.idsInColumn(edit.tableColumn());
        return selected.contains(edit.product().getId()) ? selected : List.of(edit.product().getId());
    }

    private BulkResult apply(CellEdit edit, BigDecimal number, List<Long> ids) {
        return switch (edit.column()) {
            case NAME -> bulkEditService.applyDescription(ids, edit.text());
            case CODE -> bulkEditService.applyCode(ids.get(0), edit.text());
            case MIN_STOCK -> bulkEditService.applyMinStock(ids, number);
            case STOCK -> managerAction.apply(actor -> bulkEditService.applyStockQuantity(ids, number, actor));
            case PRICE -> managerAction.apply(actor -> bulkEditService.applyPrice(ids, number, actor));
            case COST -> managerAction.apply(actor -> bulkEditService.applyCost(ids, number, actor));
            case MARGIN_PERCENT -> managerAction.apply(actor -> bulkEditService.applyMarginPercent(ids, number, actor));
        };
    }

    private BigDecimal parseNumber(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("R$", "").replace("%", "").trim();
        return DecimalInput.parseOrNull(cleaned);
    }

    private String confirmationText(EditableColumn column, String rawText, BigDecimal number, int count) {
        String target = count + " produtos";
        return switch (column) {
            case PRICE, COST -> "Aplicar " + MoneyFormat.format(number) + " em " + target + "?";
            case MARGIN_PERCENT -> "Aplicar margem de " + MoneyFormat.quantity(number) + "% em " + target + "?";
            case STOCK -> "Ajustar o estoque para " + MoneyFormat.quantity(number) + "?";
            case MIN_STOCK -> "Aplicar estoque mínimo " + MoneyFormat.quantity(number) + " em " + target + "?";
            case CODE -> "Alterar o código para \"" + rawText + "\"?";
            case NAME -> "Aplicar o nome \"" + rawText + "\" em " + target + "?";
        };
    }

    private String summary(BulkResult result) {
        String base = result.changed() + " produto(s) atualizado(s)";
        return result.skipped() > 0 ? base + " (" + result.skipped() + " sem custo)" : base;
    }

    private Outcome rejected(String message, List<Long> ids, CellEdit edit) {
        return new Outcome(false, message, ids, edit.tableColumn());
    }
}
