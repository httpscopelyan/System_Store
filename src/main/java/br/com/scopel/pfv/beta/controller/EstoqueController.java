package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.PricingSuggestionService;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class EstoqueController {

    private final ProductService productService;
    private final PricingSuggestionService pricingSuggestionService;
    private final Navigator navigator;
    private final UserSession userSession;

    @FXML private TextField codeFilter;
    @FXML private TextField nameFilter;
    @FXML private TableView<Product> table;
    @FXML private TableColumn<Product, String> colCode;
    @FXML private TableColumn<Product, String> colDesc;
    @FXML private TableColumn<Product, String> colCost;
    @FXML private TableColumn<Product, String> colMarginPercent;
    @FXML private TableColumn<Product, String> colSuggestedPrice;
    @FXML private TableColumn<Product, String> colPrice;
    @FXML private TableColumn<Product, String> colMin;
    @FXML private TableColumn<Product, String> colStock;
    @FXML private Label messageLabel;

    public EstoqueController(ProductService productService, PricingSuggestionService pricingSuggestionService,
                             Navigator navigator, UserSession userSession) {
        this.productService = productService;
        this.pricingSuggestionService = pricingSuggestionService;
        this.navigator = navigator;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        colCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCode()));
        colDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));
        colCost.setCellValueFactory(c -> new SimpleStringProperty(plain(c.getValue().getCostPrice())));
        colMarginPercent.setCellValueFactory(c -> new SimpleStringProperty(marginPercentText(c.getValue())));
        colSuggestedPrice.setCellValueFactory(c -> new SimpleStringProperty(suggestedPriceText(c.getValue())));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(plain(c.getValue().getPrice())));
        colMin.setCellValueFactory(c -> new SimpleStringProperty(formatQuantity(c.getValue().getMinStock())));
        colStock.setCellValueFactory(c -> new SimpleStringProperty(formatQuantity(c.getValue().getQuantity())));
        for (TableColumn<Product, String> col : List.of(colCost, colMarginPercent, colSuggestedPrice, colPrice, colMin, colStock)) {
            col.setStyle("-fx-alignment: CENTER-RIGHT;");
        }
        load(productService.listAll());
        Platform.runLater(() -> codeFilter.requestFocus());
    }

    private String marginPercentText(Product product) {
        BigDecimal marginPercent = pricingSuggestionService.marginPercentOf(product);
        return marginPercent == null ? "-" : marginPercent.toPlainString() + "%";
    }

    private String suggestedPriceText(Product product) {
        BigDecimal suggestedPrice = pricingSuggestionService.suggestFor(product);
        return suggestedPrice == null ? "-" : suggestedPrice.toPlainString();
    }

    private String plain(BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }

    private String formatQuantity(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private void load(List<Product> products) {
        table.setItems(FXCollections.observableArrayList(products));
        messageLabel.setText(products.size() + " produto(s)");
    }

    @FXML
    public void onFilter() {
        String code = codeFilter.getText();
        if (code != null && !code.isBlank()) {
            try {
                load(List.of(productService.findCode(code.trim())));
            } catch (ProductNotfindException e) {
                load(List.of());
                messageLabel.setText("Nenhum produto com esse código");
            }
            return;
        }
        load(productService.searchByName(nameFilter.getText()));
    }

    @FXML
    public void onClear() {
        codeFilter.clear();
        nameFilter.clear();
        load(productService.listAll());
        codeFilter.requestFocus();
    }

    @FXML
    public void onRefresh() {
        onFilter();
    }

    @FXML
    public void onNew() {
        navigator.navigate(Page.PRODUTOS);
    }

    @FXML
    public void onAlterar() {
        Product sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            messageLabel.setText("Selecione um produto para alterar.");
            return;
        }
        userSession.setProdutoEmEdicaoId(sel.getId());
        navigator.navigate(Page.EDITAR_PRODUTO);
    }

    @FXML
    public void onApagar() {
        Product sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            messageLabel.setText("Selecione um produto para inativar.");
            return;
        }
        productService.inactivate(sel.getCode());
        messageLabel.setText("Produto " + sel.getCode() + " inativado.");
        load(productService.listAll());
    }

    @FXML
    public void onRelatorios() {
        navigator.navigate(Page.RELATORIOS);
    }

    @FXML
    public void onEntrada() {
        navigator.navigate(Page.ENTRADA_ESTOQUE);
    }

    @FXML
    public void onAjuste() {
        navigator.navigate(Page.AJUSTE_ESTOQUE);
    }

    @FXML
    public void onLotes() {
        navigator.navigate(Page.LOTES);
    }

    @FXML
    public void onEtiquetas() {
        navigator.navigate(Page.ETIQUETAS);
    }
}
