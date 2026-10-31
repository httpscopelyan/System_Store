package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.controller.support.EscapeToBack;
import br.com.scopel.pfv.beta.controller.support.MoneyFormat;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceCheckController {

    private static final int RESULT_LIMIT = 100;

    private final ProductService productService;
    private final Navigator navigator;

    private final PauseTransition debounce = new PauseTransition(Duration.millis(200));

    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private TableView<Product> resultsTable;
    @FXML private TableColumn<Product, String> colCode;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, String> colPrice;
    @FXML private TableColumn<Product, String> colStock;
    @FXML private Label statusLabel;

    public PriceCheckController(ProductService productService, Navigator navigator) {
        this.productService = productService;
        this.navigator = navigator;
    }

    @FXML
    public void initialize() {
        colCode.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCode()));
        colName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDescription()));
        colPrice.setCellValueFactory(cell -> new SimpleStringProperty(MoneyFormat.format(cell.getValue().getPrice())));
        colStock.setCellValueFactory(cell -> new SimpleStringProperty(stockText(cell.getValue())));
        colPrice.getStyleClass().add("price-check-price-column");
        colStock.getStyleClass().add("price-check-stock-column");
        colPrice.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #15325f;");
        colStock.setStyle("-fx-alignment: CENTER-RIGHT;");
        resultsTable.setFocusTraversable(false);
        statusLabel.setText("Digite parte do nome ou do código");
        debounce.setOnFinished(event -> search());
        codeField.textProperty().addListener((observable, previous, current) -> debounce.playFromStart());
        nameField.textProperty().addListener((observable, previous, current) -> debounce.playFromStart());
        codeField.setOnAction(event -> search());
        nameField.setOnAction(event -> search());
        for (TextField field : List.of(codeField, nameField)) {
            field.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.DOWN && !resultsTable.getItems().isEmpty()) {
                    resultsTable.requestFocus();
                    resultsTable.getSelectionModel().selectFirst();
                }
            });
        }
        EscapeToBack.install(codeField, navigator);
        Platform.runLater(() -> {
            if (codeField.getScene() != null) {
                codeField.requestFocus();
            }
        });
    }

    @FXML
    public void onBack() {
        navigator.back();
    }

    private void search() {
        String term = firstFilled(codeField.getText(), nameField.getText());
        if (term.isEmpty()) {
            resultsTable.setItems(FXCollections.observableArrayList());
            statusLabel.setText("Digite parte do nome ou do código");
            return;
        }
        List<Product> matches = productService.searchForSale(term, RESULT_LIMIT);
        resultsTable.setItems(FXCollections.observableArrayList(matches));
        if (matches.isEmpty()) {
            statusLabel.setText("Produto não encontrado");
            return;
        }
        resultsTable.getSelectionModel().selectFirst();
        statusLabel.setText(matches.size() + " produto(s) encontrado(s)");
    }

    private String firstFilled(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private String stockText(Product product) {
        return MoneyFormat.quantity(product.getQuantity()) + " " + product.getUnit();
    }
}
