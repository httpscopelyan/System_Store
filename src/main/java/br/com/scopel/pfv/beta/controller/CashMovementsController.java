package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.controller.support.MoneyFormat;
import br.com.scopel.pfv.beta.service.cash.CashMovementRow;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class CashMovementsController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String NUMERIC_STYLE = "-fx-alignment: CENTER-RIGHT;";

    @FXML private TableView<CashMovementRow> movTable;
    @FXML private TableColumn<CashMovementRow, String> colMovDate;
    @FXML private TableColumn<CashMovementRow, String> colMovOp;
    @FXML private TableColumn<CashMovementRow, String> colMovDinheiro;
    @FXML private TableColumn<CashMovementRow, String> colMovPix;
    @FXML private TableColumn<CashMovementRow, String> colMovCredito;
    @FXML private TableColumn<CashMovementRow, String> colMovDebito;
    @FXML private TableColumn<CashMovementRow, String> colMovTotal;
    @FXML private TableColumn<CashMovementRow, String> colMovVenda;
    @FXML private TableColumn<CashMovementRow, String> colMovHist;

    @FXML
    public void initialize() {
        colMovDate.setCellValueFactory(cell -> text(DATE_TIME.format(cell.getValue().at())));
        colMovDinheiro.setCellValueFactory(cell -> text(MoneyFormat.format(cell.getValue().cash())));
        colMovPix.setCellValueFactory(cell -> text(MoneyFormat.format(cell.getValue().pix())));
        colMovCredito.setCellValueFactory(cell -> text(MoneyFormat.format(cell.getValue().credit())));
        colMovDebito.setCellValueFactory(cell -> text(MoneyFormat.format(cell.getValue().debit())));
        colMovTotal.setCellValueFactory(cell -> text(MoneyFormat.format(cell.getValue().total())));
        colMovVenda.setCellValueFactory(cell -> text(
                cell.getValue().saleId() == null ? "" : String.valueOf(cell.getValue().saleId())));
        colMovHist.setCellValueFactory(cell -> text(cell.getValue().history()));
        for (TableColumn<CashMovementRow, String> numeric : List.of(colMovDinheiro, colMovPix, colMovCredito,
                colMovDebito, colMovTotal)) {
            numeric.setStyle(NUMERIC_STYLE);
        }
        colMovOp.setCellValueFactory(cell -> text("●"));
        colMovOp.setCellFactory(column -> new EntryMarkerCell());
    }

    public void show(List<CashMovementRow> rows) {
        movTable.setItems(FXCollections.observableArrayList(rows));
    }

    public CashMovementRow selected() {
        return movTable.getSelectionModel().getSelectedItem();
    }

    public TableView<CashMovementRow> table() {
        return movTable;
    }

    private SimpleStringProperty text(String value) {
        return new SimpleStringProperty(value);
    }

    private static final class EntryMarkerCell extends TableCell<CashMovementRow, String> {

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("legend-credito", "legend-debito");
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setText(null);
                return;
            }
            CashMovementRow row = getTableRow().getItem();
            setText("●");
            getStyleClass().add(row.entry() ? "legend-credito" : "legend-debito");
        }
    }
}
