package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Edição das informações de um produto (RF04). Recebe o id do produto via
 * {@link UserSession} (selecionado no Estoque) e grava via
 * {@link ProductService#update}. Não edita o código (identidade) nem o estoque
 * (fluxo próprio). Mostra uma prévia do lucro (derivado, não armazenado).
 */
@Component
public class EditProductController {

    private final ProductService productService;
    private final Navigator navigator;
    private final UserSession userSession;

    private Long produtoId;

    @FXML private TextField codeField;
    @FXML private Label stockLabel;
    @FXML private TextField descriptionField;
    @FXML private ComboBox<Product.Unidade> unitBox;
    @FXML private TextField categoryField;
    @FXML private TextField costField;
    @FXML private TextField margemField;
    @FXML private TextField priceField;
    @FXML private TextField minStockField;
    @FXML private Label lucroLabel;
    @FXML private CheckBox activeBox;
    @FXML private Label messageLabel;

    public EditProductController(ProductService productService, Navigator navigator, UserSession userSession) {
        this.productService = productService;
        this.navigator = navigator;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        unitBox.getItems().setAll(Product.Unidade.values());

        produtoId = userSession.getProdutoEmEdicaoId();
        if (produtoId == null) {
            messageLabel.setText("Nenhum produto selecionado para edição.");
            return;
        }

        Product p = productService.findById(produtoId);
        codeField.setText(p.getCode());
        stockLabel.setText(formatQuantity(p.getQuantity()));
        descriptionField.setText(p.getDescription());
        unitBox.setValue(p.getUnit());
        categoryField.setText(p.getCategory());
        costField.setText(plain(p.getCostPrice()));
        priceField.setText(plain(p.getPrice()));
        minStockField.setText(formatQuantity(p.getMinStock()));
        activeBox.setSelected(Boolean.TRUE.equals(p.getActive()));

        // Custo ou margem → recalcula o preço (modo markup). Preço digitado à mão
        // só atualiza o lucro (não mexe na margem), evitando loop.
        costField.textProperty().addListener((o, a, b) -> onCostOrMargin());
        margemField.textProperty().addListener((o, a, b) -> onCostOrMargin());
        priceField.textProperty().addListener((o, a, b) -> updateLucro());
        updateLucro();

        Platform.runLater(() -> descriptionField.requestFocus());
    }

    @FXML
    public void onSave() {
        if (produtoId == null) {
            messageLabel.setText("Nenhum produto selecionado para edição.");
            return;
        }
        try {
            productService.update(
                    produtoId,
                    descriptionField.getText(),
                    unitBox.getValue(),
                    new BigDecimal(priceField.getText().trim()),
                    new BigDecimal(costField.getText().trim()),
                    new BigDecimal(minStockField.getText().trim()),
                    categoryField.getText(),
                    activeBox.isSelected());
            userSession.setProdutoEmEdicaoId(null);
            navigator.navigate(Page.ESTOQUE);
        } catch (NumberFormatException e) {
            messageLabel.setText("Custo, preço ou estoque mínimo inválidos.");
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
        } catch (Exception e) {
            messageLabel.setText("Não foi possível salvar. Verifique os campos.");
        }
    }

    @FXML
    public void onCancel() {
        userSession.setProdutoEmEdicaoId(null);
        navigator.navigate(Page.ESTOQUE);
    }

    /**
     * Modo markup: com custo + margem válidos, calcula o preço de venda
     * (custo × (1 + margem/100)). Margem em branco = modo preço direto (não
     * mexe no preço). Em qualquer caso, atualiza a prévia do lucro.
     */
    private void onCostOrMargin() {
        BigDecimal cost = parseOrNull(costField.getText());
        BigDecimal margem = parseOrNull(margemField.getText());
        if (cost != null && cost.compareTo(BigDecimal.ZERO) >= 0
                && margem != null && !margemField.getText().isBlank()) {
            BigDecimal factor = BigDecimal.ONE.add(margem.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
            BigDecimal price = cost.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            priceField.setText(price.toPlainString()); // dispara updateLucro() via listener do preço
        }
        updateLucro();
    }

    /** Prévia do lucro (R$ e % sobre o custo) — derivado, nunca armazenado. */
    private void updateLucro() {
        BigDecimal cost = parseOrNull(costField.getText());
        BigDecimal price = parseOrNull(priceField.getText());
        if (cost == null || price == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            lucroLabel.setText("-");
            return;
        }
        BigDecimal lucro = price.subtract(cost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pct = lucro.multiply(new BigDecimal("100")).divide(cost, 1, RoundingMode.HALF_UP);
        lucroLabel.setText("R$ " + lucro.toPlainString() + " (" + pct.toPlainString() + "%)");
    }

    private BigDecimal parseOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
}
