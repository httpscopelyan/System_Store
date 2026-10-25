package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Purchase;
import br.com.scopel.pfv.beta.model.PurchaseItem;
import br.com.scopel.pfv.beta.model.Supplier;
import br.com.scopel.pfv.beta.service.PurchaseService;
import br.com.scopel.pfv.beta.service.SupplierService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final SupplierService supplierService;
    private final UserSession userSession;

    private Purchase current;

    @FXML private TextField supplierIdField;
    @FXML private TextField invoiceField;
    @FXML private Label statusLabel;
    @FXML private TextField codeField;
    @FXML private TextField qtyField;
    @FXML private TextField costField;
    @FXML private TableView<PurchaseItem> table;
    @FXML private TableColumn<PurchaseItem, String> colProduct;
    @FXML private TableColumn<PurchaseItem, String> colQty;
    @FXML private TableColumn<PurchaseItem, String> colCost;
    @FXML private TableColumn<PurchaseItem, String> colTotal;
    @FXML private Label totalLabel;
    @FXML private Label messageLabel;

    public PurchaseController(PurchaseService purchaseService, SupplierService supplierService,
                             UserSession userSession) {
        this.purchaseService = purchaseService;
        this.supplierService = supplierService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        colProduct.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProduct().getDescription()));
        colQty.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getQuantity().toPlainString()));
        colCost.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUnitCost().toPlainString()));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTotalCost().toPlainString()));
    }

    @FXML
    public void onOpen() {
        try {
            Supplier supplier = supplierService.findById(Long.parseLong(supplierIdField.getText().trim()));
            current = purchaseService.openPurchase(supplier, invoiceField.getText(), userSession.getLoggedUser());
            statusLabel.setText("Compra #" + current.getId() + " aberta (" + supplier.getName() + ")");
            refresh();
            messageLabel.setText("");
        } catch (NumberFormatException e) {
            messageLabel.setText("ID de fornecedor inválido");
        } catch (Exception e) {
            messageLabel.setText("Fornecedor não encontrado");
        }
    }

    @FXML
    public void onAddItem() {
        if (current == null) {
            messageLabel.setText("Abra uma compra primeiro");
            return;
        }
        try {
            BigDecimal qty = new BigDecimal(qtyField.getText().trim().replace(',', '.'));
            BigDecimal cost = new BigDecimal(costField.getText().trim().replace(',', '.'));
            current = purchaseService.addItem(current, codeField.getText().trim(), qty, cost);
            codeField.clear();
            qtyField.clear();
            costField.clear();
            refresh();
        } catch (NumberFormatException e) {
            messageLabel.setText("Qtd ou custo inválidos");
        } catch (Exception e) {
            messageLabel.setText("Produto não encontrado");
        }
    }

    @FXML
    public void onReceive() {
        if (current == null) {
            messageLabel.setText("Abra uma compra primeiro");
            return;
        }
        try {
            current = purchaseService.receive(current, userSession.getLoggedUser());
            statusLabel.setText("Compra #" + current.getId() + " RECEBIDA");
            messageLabel.setText("Estoque atualizado");
            current = null;
            refresh();
        } catch (Exception e) {
            messageLabel.setText(e.getMessage());
        }
    }

    private void refresh() {
        if (current == null) {
            table.setItems(FXCollections.observableArrayList());
            totalLabel.setText("Total: R$ 0,00");
        } else {
            table.setItems(FXCollections.observableArrayList(current.getItems()));
            totalLabel.setText("Total: R$ " + current.getTotal().toPlainString());
        }
    }
}
