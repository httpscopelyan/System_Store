package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.BetaApplication;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;

@Component
public class SaleController {

    private final SaleService saleService;
    private final UserSession userSession;

    private Sale vendaAtual;

    private final ObservableList<SaleItemRow> rows = FXCollections.observableArrayList();

    @FXML private TextField codeField;
    @FXML private TableView<SaleItemRow> itemsTable;
    @FXML private TableColumn<SaleItemRow, String> descriptionCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> quantityCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> unitPriceCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> totalPriceCol;
    @FXML private Label totalLabel;
    @FXML private Label statusLabel;

    public SaleController(SaleService saleService, UserSession userSession) {
        this.saleService = saleService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        unitPriceCol.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        totalPriceCol.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));

        itemsTable.setItems(rows);

        vendaAtual = saleService.openSale(
                userSession.getLoggedUser(),
                userSession.getCaixaAtual()
        );

        codeField.requestFocus();
    }

    @FXML
    public void onAddItem() {
        try {

            String code = codeField.getText();
            vendaAtual = saleService.addItem(vendaAtual, code, BigDecimal.ONE);

            refreshTable();
            codeField.clear();
            codeField.requestFocus();

        } catch (ProductNotfindException e) {
            statusLabel.setText("Produto não encontrado");
            codeField.clear();
            codeField.requestFocus();
        }
    }

    @FXML
    public void onFinalize() {
        if (vendaAtual == null) return;
        try {
            vendaAtual = saleService.goToPayment(vendaAtual);
            userSession.setVendaAtual(vendaAtual);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pagamento.fxml"));
            loader.setControllerFactory(BetaApplication.springContext::getBean);
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) codeField.getScene().getWindow();
            stage.setScene(scene);

        } catch (IllegalSaleStateException e) {
            statusLabel.setText("Adicione itens antes de finalizar");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void refreshTable() {
        rows.clear();
        for (SaleItem item : vendaAtual.getItems()) {
            rows.add(new SaleItemRow(
                    item.getProduct().getDescription(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTotalPrice()
            ));
        }
        totalLabel.setText("R$ " + vendaAtual.getTotal());
    }
}