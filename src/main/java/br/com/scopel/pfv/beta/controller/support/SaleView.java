package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.controller.SaleItemRow;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.service.ReceiptService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Window;

import java.math.BigDecimal;

public final class SaleView {

    private static final String EMPTY_TOTAL = "R$ 0,00";
    private static final String NO_LAST_ITEM = "—";

    private final ObservableList<SaleItemRow> rows = FXCollections.observableArrayList();
    private final Label totalLabel;
    private final Label statusLabel;
    private final Label lastItemLabel;
    private final TextArea receiptPreview;
    private final ReceiptService receiptService;

    public SaleView(TableView<SaleItemRow> itemsTable,
                    TableColumn<SaleItemRow, String> descriptionColumn,
                    TableColumn<SaleItemRow, BigDecimal> quantityColumn,
                    TableColumn<SaleItemRow, BigDecimal> unitPriceColumn,
                    TableColumn<SaleItemRow, BigDecimal> totalPriceColumn,
                    Label totalLabel, Label statusLabel, Label lastItemLabel, TextArea receiptPreview,
                    ReceiptService receiptService) {
        this.totalLabel = totalLabel;
        this.statusLabel = statusLabel;
        this.lastItemLabel = lastItemLabel;
        this.receiptPreview = receiptPreview;
        this.receiptService = receiptService;
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        unitPriceColumn.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        totalPriceColumn.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));
        itemsTable.setItems(rows);
        itemsTable.setFocusTraversable(false);
    }

    public void reset() {
        rows.clear();
        totalLabel.setText(EMPTY_TOTAL);
        statusLabel.setText("");
        if (receiptPreview != null) {
            receiptPreview.clear();
        }
        showLastItem(null);
    }

    public void showSale(Sale sale) {
        rows.clear();
        if (sale == null) {
            totalLabel.setText(EMPTY_TOTAL);
            if (receiptPreview != null) {
                receiptPreview.clear();
            }
            return;
        }
        for (SaleItem item : sale.getItems()) {
            rows.add(new SaleItemRow(item.getProduct().getDescription(), item.getQuantity(),
                    item.getUnitPrice(), item.getTotalPrice()));
        }
        totalLabel.setText("R$ " + sale.getTotal());
        if (receiptPreview != null) {
            receiptPreview.setText(receiptService.buildSaleReceipt(sale));
        }
    }

    public void showStatus(String message) {
        statusLabel.setText(message);
    }

    public void showLastItem(SaleItem item) {
        if (lastItemLabel == null) {
            return;
        }
        lastItemLabel.setText(item == null || item.getProduct() == null
                ? NO_LAST_ITEM
                : item.getProduct().getDescription());
    }

    public void showPending(Product product) {
        if (lastItemLabel != null) {
            lastItemLabel.setText(product.getDescription() + "  —  " + MoneyFormat.format(product.getPrice()));
        }
    }

    public SaleItem lastItemOf(Sale sale) {
        if (sale == null || sale.getItems().isEmpty()) {
            return null;
        }
        return sale.getItems().get(sale.getItems().size() - 1);
    }

    public SaleItem itemByCode(Sale sale, String productCode) {
        if (sale == null || productCode == null) {
            return null;
        }
        String trimmed = productCode.trim();
        for (SaleItem item : sale.getItems()) {
            if (item.getProduct() != null && trimmed.equalsIgnoreCase(item.getProduct().getCode())) {
                return item;
            }
        }
        return null;
    }

    public Window window() {
        Scene scene = statusLabel.getScene();
        return scene == null ? null : scene.getWindow();
    }
}
