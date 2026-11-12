package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.controller.support.SaleDetailAction;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.SaleHistoryService;
import br.com.scopel.pfv.beta.service.SaleReprintService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class HistoricoVendasController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    private final SaleService saleService;
    private final SaleHistoryService saleHistoryService;
    private final SaleReprintService saleReprintService;

    private SaleDetailAction saleDetailAction;

    @FXML private TableView<Sale> table;
    @FXML private TableColumn<Sale, String> colDate;
    @FXML private TableColumn<Sale, String> colId;
    @FXML private TableColumn<Sale, String> colOperator;
    @FXML private TableColumn<Sale, String> colCustomer;
    @FXML private TableColumn<Sale, String> colTotal;
    @FXML private Label statusLabel;

    public HistoricoVendasController(SaleService saleService, SaleHistoryService saleHistoryService,
                                     SaleReprintService saleReprintService) {
        this.saleService = saleService;
        this.saleHistoryService = saleHistoryService;
        this.saleReprintService = saleReprintService;
    }

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCreatedAt() == null ? "" : DT.format(c.getValue().getCreatedAt())));
        colId.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getId() == null ? "-" : String.valueOf(c.getValue().getId())));
        colOperator.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getOperator() == null ? "-" : c.getValue().getOperator().getName()));
        colCustomer.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCustomer() == null ? "CONSUMIDOR" : c.getValue().getCustomer().getName()));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().getTotal())));
        colTotal.setStyle("-fx-alignment: CENTER-RIGHT;");

        saleDetailAction = new SaleDetailAction(saleHistoryService, saleReprintService, () -> table.getScene().getWindow());
        table.setOnMouseClicked(click -> {
            if (click.getButton() == MouseButton.PRIMARY && click.getClickCount() == 2) {
                onViewItems();
            }
        });
        table.setOnKeyPressed(key -> {
            if (key.getCode() == KeyCode.ENTER) {
                onViewItems();
                key.consume();
            }
        });

        List<Sale> history = saleService.history();
        table.setItems(FXCollections.observableArrayList(history));
        statusLabel.setText(history.isEmpty() ? "Nenhuma venda finalizada ainda." : history.size() + " venda(s)");
    }

    @FXML
    public void onViewItems() {
        Sale selectedSale = table.getSelectionModel().getSelectedItem();
        String message = saleDetailAction.showItemsOf(
                selectedSale == null ? null : selectedSale.getId(), selectedSale != null);
        if (!message.isEmpty()) {
            statusLabel.setText(message);
        }
    }

    private String money(BigDecimal v) {
        return "R$ " + MONEY.format(v == null ? BigDecimal.ZERO : v);
    }
}
