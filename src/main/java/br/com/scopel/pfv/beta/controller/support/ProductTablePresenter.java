package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.PricingSuggestionService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DefaultStringConverter;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ProductTablePresenter {

    public enum EditableColumn {
        NAME,
        COST,
        MARGIN_PERCENT,
        PRICE,
        MIN_STOCK
    }

    public record CellEdit(EditableColumn column, TableColumn<Product, String> tableColumn, Product product,
                           String text) {
    }

    public record Columns(TableColumn<Product, String> handle, TableColumn<Product, String> code,
                          TableColumn<Product, String> description, TableColumn<Product, String> cost,
                          TableColumn<Product, String> profit, TableColumn<Product, String> marginPercent,
                          TableColumn<Product, String> suggestedPrice, TableColumn<Product, String> price,
                          TableColumn<Product, String> minStock, TableColumn<Product, String> stock) {
    }

    private static final String HANDLE_GLYPH = "⋮";
    private static final String NUMERIC_STYLE = "-fx-alignment: CENTER-RIGHT;";

    private final TableView<Product> table;
    private final Columns columns;
    private final PricingSuggestionService pricing;
    private final ProductSelection selection;
    private final Consumer<CellEdit> onEdit;
    private final Map<EditableColumn, TableColumn<Product, String>> editable = new EnumMap<>(EditableColumn.class);

    public ProductTablePresenter(TableView<Product> table, Columns columns, PricingSuggestionService pricing,
                                 ProductSelection selection, boolean manager, Consumer<CellEdit> onEdit) {
        this.table = table;
        this.columns = columns;
        this.pricing = pricing;
        this.selection = selection;
        this.onEdit = onEdit;
        configureTable();
        configureValues();
        configureHandle();
        configureEditable();
        showMargins(manager);
        for (TableColumn<Product, String> numeric : List.of(columns.cost(), columns.profit(),
                columns.marginPercent(), columns.suggestedPrice(), columns.price(), columns.minStock(),
                columns.stock())) {
            numeric.setStyle(NUMERIC_STYLE);
        }
        if (!manager) {
            managerOnlyColumns().forEach(column -> column.setVisible(false));
        }
    }

    public void show(List<Product> products) {
        table.setItems(FXCollections.observableArrayList(products));
    }

    public void showMargins(boolean visible) {
        managerOnlyColumns().forEach(column -> column.setVisible(visible));
    }

    public boolean isPriceColumn(TableColumn<Product, ?> column) {
        return column == columns.price();
    }

    public TableColumn<Product, String> priceColumn() {
        return columns.price();
    }

    public List<TableColumn<Product, String>> managerOnlyColumns() {
        return List.of(columns.cost(), columns.profit(), columns.marginPercent(), columns.suggestedPrice());
    }

    private void configureTable() {
        table.setEditable(true);
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
    }

    private void configureValues() {
        columns.code().setCellValueFactory(cell -> text(cell.getValue().getCode()));
        columns.description().setCellValueFactory(cell -> text(cell.getValue().getDescription()));
        columns.cost().setCellValueFactory(cell -> text(plain(cell.getValue().getCostPrice())));
        columns.profit().setCellValueFactory(cell -> text(money(pricing.profitOf(cell.getValue()))));
        columns.marginPercent().setCellValueFactory(cell -> text(percent(pricing.marginPercentOf(cell.getValue()))));
        columns.suggestedPrice().setCellValueFactory(cell -> text(money(pricing.suggestFor(cell.getValue()))));
        columns.price().setCellValueFactory(cell -> text(plain(cell.getValue().getPrice())));
        columns.minStock().setCellValueFactory(cell -> text(quantity(cell.getValue().getMinStock())));
        columns.stock().setCellValueFactory(cell -> text(quantity(cell.getValue().getQuantity())));
    }

    private void configureHandle() {
        TableColumn<Product, String> handle = columns.handle();
        handle.setCellValueFactory(cell -> text(HANDLE_GLYPH));
        handle.setSortable(false);
        handle.setReorderable(false);
        handle.setResizable(false);
        handle.setEditable(false);
        handle.setMinWidth(30);
        handle.setMaxWidth(30);
        handle.setCellFactory(column -> new TableCell<>() {
            {
                getStyleClass().add("row-handle");
                setOnMouseClicked(event -> {
                    if (!isEmpty()) {
                        selection.selectWholeRow(getIndex(), event.isControlDown() || event.isShiftDown());
                        event.consume();
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });
    }

    private void configureEditable() {
        register(EditableColumn.NAME, columns.description());
        register(EditableColumn.COST, columns.cost());
        register(EditableColumn.MARGIN_PERCENT, columns.marginPercent());
        register(EditableColumn.PRICE, columns.price());
        register(EditableColumn.MIN_STOCK, columns.minStock());
        for (TableColumn<Product, String> readOnly : List.of(columns.code(), columns.profit(),
                columns.suggestedPrice(), columns.stock())) {
            readOnly.setEditable(false);
        }
    }

    private void register(EditableColumn kind, TableColumn<Product, String> column) {
        editable.put(kind, column);
        column.setEditable(true);
        column.setCellFactory(TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
        column.setOnEditCommit(event ->
                onEdit.accept(new CellEdit(kind, column, event.getRowValue(), event.getNewValue())));
    }

    private SimpleStringProperty text(String value) {
        return new SimpleStringProperty(value == null ? "" : value);
    }

    private String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String money(BigDecimal value) {
        return value == null ? "-" : value.toPlainString();
    }

    private String percent(BigDecimal value) {
        return value == null ? "-" : value.toPlainString() + "%";
    }

    private String quantity(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
