package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.Product;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProductSelection {

    private final TableView<Product> table;

    public ProductSelection(TableView<Product> table) {
        this.table = table;
    }

    public List<TableColumn<Product, ?>> selectedColumns() {
        Set<TableColumn<Product, ?>> columns = new LinkedHashSet<>();
        for (TablePosition<?, ?> position : table.getSelectionModel().getSelectedCells()) {
            @SuppressWarnings("unchecked")
            TableColumn<Product, ?> column = (TableColumn<Product, ?>) position.getTableColumn();
            if (column != null) {
                columns.add(column);
            }
        }
        return new ArrayList<>(columns);
    }

    public boolean isSingleColumn() {
        return selectedColumns().size() == 1;
    }

    public TableColumn<Product, ?> selectedColumn() {
        List<TableColumn<Product, ?>> columns = selectedColumns();
        return columns.size() == 1 ? columns.get(0) : null;
    }

    public List<Product> productsInColumn(TableColumn<Product, ?> column) {
        Set<Integer> rows = new LinkedHashSet<>();
        for (TablePosition<?, ?> position : table.getSelectionModel().getSelectedCells()) {
            if (position.getTableColumn() == column) {
                rows.add(position.getRow());
            }
        }
        List<Product> products = new ArrayList<>();
        for (int row : rows) {
            if (row >= 0 && row < table.getItems().size()) {
                products.add(table.getItems().get(row));
            }
        }
        return products;
    }

    public List<Long> idsInColumn(TableColumn<Product, ?> column) {
        return productsInColumn(column).stream().map(Product::getId).toList();
    }

    public List<Product> selectedProducts() {
        Set<Integer> rows = new LinkedHashSet<>();
        for (TablePosition<?, ?> position : table.getSelectionModel().getSelectedCells()) {
            rows.add(position.getRow());
        }
        List<Product> products = new ArrayList<>();
        for (int row : rows) {
            if (row >= 0 && row < table.getItems().size()) {
                products.add(table.getItems().get(row));
            }
        }
        return products;
    }

    public Product firstProduct() {
        List<Product> products = selectedProducts();
        return products.isEmpty() ? null : products.get(0);
    }

    public int rowCount() {
        return selectedProducts().size();
    }

    public void selectWholeRow(int rowIndex, boolean extend) {
        if (rowIndex < 0 || rowIndex >= table.getItems().size()) {
            return;
        }
        if (!extend) {
            table.getSelectionModel().clearSelection();
        }
        for (TableColumn<Product, ?> column : table.getColumns()) {
            if (column.isVisible()) {
                table.getSelectionModel().select(rowIndex, column);
            }
        }
    }

    public void restore(Collection<Long> productIds, TableColumn<Product, ?> column) {
        table.getSelectionModel().clearSelection();
        if (column == null) {
            return;
        }
        for (int row = 0; row < table.getItems().size(); row++) {
            if (productIds.contains(table.getItems().get(row).getId())) {
                table.getSelectionModel().select(row, column);
            }
        }
    }
}
