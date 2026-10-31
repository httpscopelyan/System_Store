package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.service.alert.StockAlert;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.math.BigDecimal;
import java.util.List;

public final class ShoppingListPresenter {

    private static final String CRITICAL_STYLE_CLASS = "alert-critical";
    private static final String WARNING_STYLE_CLASS = "alert-warning";

    private final TableView<StockAlert> table;

    public ShoppingListPresenter(TableView<StockAlert> table,
                                 TableColumn<StockAlert, String> codeColumn,
                                 TableColumn<StockAlert, String> descriptionColumn,
                                 TableColumn<StockAlert, String> stockColumn,
                                 TableColumn<StockAlert, String> minStockColumn,
                                 TableColumn<StockAlert, String> differenceColumn,
                                 TableColumn<StockAlert, String> restockColumn,
                                 TableColumn<StockAlert, String> statusColumn) {
        this.table = table;
        codeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().code()));
        descriptionColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().description()));
        stockColumn.setCellValueFactory(cell -> new SimpleStringProperty(quantity(cell.getValue().quantity())));
        minStockColumn.setCellValueFactory(cell -> new SimpleStringProperty(quantity(cell.getValue().minStock())));
        differenceColumn.setCellValueFactory(cell -> new SimpleStringProperty(quantity(cell.getValue().difference())));
        restockColumn.setCellValueFactory(cell -> new SimpleStringProperty(quantity(cell.getValue().suggestedQuantity())));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(statusText(cell.getValue())));
        for (TableColumn<StockAlert, String> numeric :
                List.of(stockColumn, minStockColumn, differenceColumn, restockColumn)) {
            numeric.setStyle("-fx-alignment: CENTER-RIGHT;");
        }
        table.setRowFactory(view -> new SeverityRow());
    }

    public void show(List<StockAlert> alerts) {
        table.setItems(FXCollections.observableArrayList(alerts));
    }

    public StockAlert selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    private String statusText(StockAlert alert) {
        return alert.severity() == StockAlert.Severity.CRITICO ? "Crítico" : "Atenção";
    }

    private String quantity(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static final class SeverityRow extends TableRow<StockAlert> {

        @Override
        protected void updateItem(StockAlert alert, boolean empty) {
            super.updateItem(alert, empty);
            getStyleClass().removeAll(CRITICAL_STYLE_CLASS, WARNING_STYLE_CLASS);
            if (empty || alert == null) {
                return;
            }
            getStyleClass().add(alert.severity() == StockAlert.Severity.CRITICO
                    ? CRITICAL_STYLE_CLASS
                    : WARNING_STYLE_CLASS);
        }
    }
}
