package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.service.SaleHistoryEntry;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class SaleDetailDialog {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String ANONYMOUS_CUSTOMER = "CONSUMIDOR";
    private static final double DIALOG_WIDTH = 640;
    private static final double ITEMS_TABLE_HEIGHT = 260;

    private SaleDetailDialog() {
    }

    public static void show(Window owner, SaleHistoryEntry sale) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Itens da venda");
        dialog.setHeaderText("Venda #" + sale.saleId() + (sale.createdAt() == null ? ""
                : "  —  " + DATE_TIME.format(sale.createdAt())));
        dialog.getDialogPane().setContent(contentOf(sale));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private static VBox contentOf(SaleHistoryEntry sale) {
        VBox content = new VBox(8);
        content.setPadding(new Insets(12));
        content.setPrefWidth(DIALOG_WIDTH);
        content.getChildren().add(label("Operador: " + valueOrDash(sale.operatorName()), "win-label-bold"));
        content.getChildren().add(label("Cliente: " + customerOf(sale), "win-label"));
        content.getChildren().add(itemsTable(sale.items()));
        content.getChildren().addAll(totalsLabels(sale));
        content.getChildren().add(label("Pagamento:", "win-label-bold"));
        sale.payments().forEach(paymentPart -> content.getChildren().add(label(
                SplitPaymentView.describe(paymentPart.method(), paymentPart.cardType(),
                        paymentPart.amountReceived(), paymentPart.change()), "win-label")));
        return content;
    }

    private static TableView<SaleHistoryEntry.Item> itemsTable(List<SaleHistoryEntry.Item> items) {
        TableView<SaleHistoryEntry.Item> itemsTable = new TableView<>();
        itemsTable.getStyleClass().add("items-table");
        itemsTable.setPrefHeight(ITEMS_TABLE_HEIGHT);
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        itemsTable.getColumns().add(column("Produto", 280, item -> item.productDescription()));
        itemsTable.getColumns().add(column("Qtd.", 70, item -> MoneyFormat.quantity(item.quantity())));
        itemsTable.getColumns().add(column("Vr. Unit.", 100, item -> MoneyFormat.format(item.unitPrice())));
        itemsTable.getColumns().add(column("Subtotal", 100, item -> MoneyFormat.format(item.subtotal())));
        itemsTable.getItems().setAll(items);
        return itemsTable;
    }

    private static TableColumn<SaleHistoryEntry.Item, String> column(
            String title, double width, Function<SaleHistoryEntry.Item, String> cellText) {
        TableColumn<SaleHistoryEntry.Item, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(cellText.apply(cell.getValue())));
        return column;
    }

    private static List<Label> totalsLabels(SaleHistoryEntry sale) {
        List<Label> totalsLabels = new ArrayList<>();
        if (isPositive(sale.discount())) {
            totalsLabels.add(label("Desconto: -" + MoneyFormat.format(sale.discount()), "win-label"));
        }
        if (isPositive(sale.surcharge())) {
            totalsLabels.add(label("Acréscimo: +" + MoneyFormat.format(sale.surcharge()), "win-label"));
        }
        totalsLabels.add(label("TOTAL: " + MoneyFormat.format(sale.total()), "win-label-bold"));
        return totalsLabels;
    }

    private static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    private static String customerOf(SaleHistoryEntry sale) {
        return sale.customerName() == null ? ANONYMOUS_CUSTOMER : sale.customerName();
    }

    private static String valueOrDash(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
