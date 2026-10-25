package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.model.PriceTable;
import br.com.scopel.pfv.beta.model.PriceTableItem;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.PriceTableService;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PriceTableController {

    private final PriceTableService priceTableService;
    private final ProductService productService;

    @FXML private TextField tableNameField;
    @FXML private ComboBox<PriceTable> tableBox;
    @FXML private TextField productCodeField;
    @FXML private TextField priceField;
    @FXML private TableView<PriceTableItem> table;
    @FXML private TableColumn<PriceTableItem, String> colProduct;
    @FXML private TableColumn<PriceTableItem, String> colPrice;
    @FXML private Label messageLabel;

    public PriceTableController(PriceTableService priceTableService, ProductService productService) {
        this.priceTableService = priceTableService;
        this.productService = productService;
    }

    @FXML
    public void initialize() {
        colProduct.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProduct().getDescription()));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPrice().toPlainString()));
        tableBox.setConverter(new StringConverter<>() {
            @Override public String toString(PriceTable t) { return t == null ? "" : t.getName(); }
            @Override public PriceTable fromString(String s) { return null; }
        });
        refreshTables();
    }

    private void refreshTables() {
        tableBox.setItems(FXCollections.observableArrayList(priceTableService.listActiveTables()));
    }

    @FXML
    public void onCreateTable() {
        try {
            String name = tableNameField.getText();
            if (name == null || name.isBlank()) {
                messageLabel.setText("Informe o nome da tabela");
                return;
            }
            priceTableService.createTable(name.trim());
            tableNameField.clear();
            refreshTables();
            messageLabel.setText("Tabela criada");
        } catch (Exception e) {
            messageLabel.setText("Não foi possível criar (nome duplicado?)");
        }
    }

    @FXML
    public void onLoad() {
        PriceTable selected = tableBox.getValue();
        if (selected == null) {
            messageLabel.setText("Selecione uma tabela");
            return;
        }
        table.setItems(FXCollections.observableArrayList(priceTableService.items(selected.getId())));
    }

    @FXML
    public void onSetPrice() {
        PriceTable selected = tableBox.getValue();
        if (selected == null) {
            messageLabel.setText("Selecione uma tabela");
            return;
        }
        try {
            Product product = productService.findCode(productCodeField.getText().trim());
            BigDecimal price = new BigDecimal(priceField.getText().trim().replace(',', '.'));
            priceTableService.setPrice(selected, product, price);
            productCodeField.clear();
            priceField.clear();
            messageLabel.setText("Preço definido");
            onLoad();
        } catch (NumberFormatException e) {
            messageLabel.setText("Preço inválido");
        } catch (Exception e) {
            messageLabel.setText("Produto não encontrado");
        }
    }
}
