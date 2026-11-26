package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.controller.SaleItemRow;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.math.BigDecimal;
import java.util.function.BiFunction;

public final class SaleItemCell extends TableCell<SaleItemRow, BigDecimal> {

    private final BiFunction<SaleItemRow, BigDecimal, String> text;

    private SaleItemCell(BiFunction<SaleItemRow, BigDecimal, String> text) {
        this.text = text;
    }

    public static void install(TableColumn<SaleItemRow, BigDecimal> quantityColumn,
                               TableColumn<SaleItemRow, BigDecimal> unitPriceColumn) {
        quantityColumn.setCellFactory(column -> new SaleItemCell((row, quantity) ->
                row.getUnit().pricedPerHundredGrams()
                        ? row.getUnit().soldQuantityText(quantity)
                        : quantity.toString()));
        unitPriceColumn.setCellFactory(column -> new SaleItemCell((row, price) ->
                price + row.getUnit().priceSuffix()));
    }

    @Override
    protected void updateItem(BigDecimal value, boolean empty) {
        super.updateItem(value, empty);
        if (empty || value == null || getIndex() >= getTableView().getItems().size()) {
            setText(null);
            return;
        }
        setText(text.apply(getTableView().getItems().get(getIndex()), value));
    }
}
