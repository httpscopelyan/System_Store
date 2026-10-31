package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.CashRegister;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.format.DateTimeFormatter;
import java.util.List;

public final class CashHistoryPresenter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final TableView<CashRegister> table;

    public CashHistoryPresenter(TableView<CashRegister> table, TableColumn<CashRegister, String> dateColumn,
                                TableColumn<CashRegister, String> balanceColumn) {
        this.table = table;
        dateColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getOpenedAt() == null ? "" : DATE_TIME.format(cell.getValue().getOpenedAt())));
        balanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getClosingAmount() == null ? "-"
                        : MoneyFormat.format(cell.getValue().getClosingAmount())));
    }

    public void show(List<CashRegister> history) {
        table.setItems(FXCollections.observableArrayList(history));
    }
}
