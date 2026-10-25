package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class EstoqueController {

    private final ProductService productService;
    private final Navigator navigator;
    private final UserSession userSession;

    @FXML private TextField codeFilter;
    @FXML private TextField nameFilter;
    @FXML private TableView<Product> table;
    @FXML private TableColumn<Product, String> colCode;
    @FXML private TableColumn<Product, String> colDesc;
    @FXML private TableColumn<Product, String> colCost;
    @FXML private TableColumn<Product, String> colLucro;
    @FXML private TableColumn<Product, String> colSugest;
    @FXML private TableColumn<Product, String> colPrice;
    @FXML private TableColumn<Product, String> colMin;
    @FXML private TableColumn<Product, String> colStock;
    @FXML private Label messageLabel;

    public EstoqueController(ProductService productService, Navigator navigator, UserSession userSession) {
        this.productService = productService;
        this.navigator = navigator;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        colCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCode()));
        colDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));
        colCost.setCellValueFactory(c -> new SimpleStringProperty(plain(c.getValue().getCostPrice())));
        colLucro.setCellValueFactory(c -> new SimpleStringProperty(lucroPercent(c.getValue())));
        colSugest.setCellValueFactory(c -> new SimpleStringProperty(sugestao(c.getValue())));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(plain(c.getValue().getPrice())));
        colMin.setCellValueFactory(c -> new SimpleStringProperty(formatQuantity(c.getValue().getMinStock())));
        colStock.setCellValueFactory(c -> new SimpleStringProperty(formatQuantity(c.getValue().getQuantity())));
        for (TableColumn<Product, String> col : List.of(colCost, colLucro, colSugest, colPrice, colMin, colStock)) {
            col.setStyle("-fx-alignment: CENTER-RIGHT;");
        }
        load(productService.listAll());
        // Bipe: foco no código ao abrir a página.
        Platform.runLater(() -> codeFilter.requestFocus());
    }

    private String lucroPercent(Product p) {
        BigDecimal cost = p.getCostPrice();
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return "-";
        }
        return p.getPrice().subtract(cost)
                .multiply(new BigDecimal("100"))
                .divide(cost, 1, RoundingMode.HALF_UP)
                .toPlainString() + "%";
    }

    private String sugestao(Product p) {
        BigDecimal cost = p.getCostPrice();
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return "-";
        }
        return cost.multiply(new BigDecimal("1.30")).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Formata DINHEIRO (custo/preço): mantém as casas decimais do valor armazenado. */
    private String plain(BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }

    /**
     * Formata QUANTIDADE (estoque e estoque mínimo) para exibição, sem zeros à
     * direita: 32.000 → "32", 1.250 → "1.25", 0 → "0". Apenas visual — não
     * altera o {@link BigDecimal} armazenado nem o banco.
     */
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
