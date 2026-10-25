package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Consulta de preço rápida (RF07/RF05, reusa RF04). Tela DEDICADA, só leitura:
 * NUNCA abre venda, NUNCA chama addItem, NUNCA baixa estoque nem grava nada.
 * Reusa exclusivamente {@link ProductService#findCode(String)} e
 * {@link ProductService#searchByName(String)}. O preço exibido vem direto do
 * cadastro — aqui não há arredondamento/cálculo, apenas formatação pt-BR.
 */
@Component
public class PriceCheckController {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    private final ProductService productService;
    private final Navigator navigator;

    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private Label descriptionLabel;
    @FXML private Label priceLabel;
    @FXML private Label unitLabel;
    @FXML private Label statusLabel;

    public PriceCheckController(ProductService productService, Navigator navigator) {
        this.productService = productService;
        this.navigator = navigator;
    }

    @FXML
    public void initialize() {
        clearResult();
        statusLabel.setText("");
        // RNF03: Esc volta sem efeito colateral. Instalado na cena quando a página entra.
        codeField.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                installEscToBack(newScene);
            }
        });
        focusCode();
    }

    /**
     * Enter nos campos (ou o botão Buscar) dispara a consulta. Prioriza o código
     * quando preenchido (RF04); senão usa o nome (RF05).
     */
    @FXML
    public void onSearch(ActionEvent event) {
        String code = codeField.getText() == null ? "" : codeField.getText().trim();
        if (!code.isEmpty()) {
            searchByCode(code);
            return;
        }
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (!name.isEmpty()) {
            searchByName(name);
            return;
        }
        clearResult();
        statusLabel.setText("Informe um código ou um nome para consultar");
    }

    /**
     * Busca por código exato (código de barras = String; passa o texto cru, só
     * aparado, preservando zeros à esquerda). Produto ausente é erro explícito em
     * PT, sem catch vazio e sem derrubar a tela.
     */
    public void searchByCode(String code) {
        try {
            showProduct(productService.findCode(code));
        } catch (ProductNotfindException productNotFound) {
            clearResult();
            statusLabel.setText("Produto não encontrado");
        }
        focusCode();
    }

    /**
     * Busca por nome (parcial). Mostra o primeiro resultado em destaque; se houver
     * mais de um, avisa para refinar. Nenhum resultado = erro explícito em PT.
     */
    public void searchByName(String name) {
        List<Product> matches = productService.searchByName(name);
        if (matches.isEmpty()) {
            clearResult();
            statusLabel.setText("Produto não encontrado");
            return;
        }
        showProduct(matches.get(0));
        if (matches.size() > 1) {
            statusLabel.setText("Mostrando o 1º de " + matches.size() + " resultados — refine o nome");
        }
    }

    private void showProduct(Product product) {
        descriptionLabel.setText(product.getDescription());
        priceLabel.setText("R$ " + MONEY.format(product.getPrice()));
        unitLabel.setText("Unidade: " + product.getUnit() + "   Código: " + product.getCode());
        statusLabel.setText("");
    }

    private void clearResult() {
        descriptionLabel.setText("—");
        priceLabel.setText("R$ 0,00");
        unitLabel.setText("");
    }

    @FXML
    public void onBack() {
        // Volta para a tela anterior (Consulta é auxiliar). Sem efeito colateral:
        // nada foi aberto/gravado. Sem histórico conhecido, cai no Início.
        Page previous = navigator.getPreviousPage();
        navigator.navigate(previous != null && previous != Page.CONSULTA_PRECO ? previous : Page.INICIO);
    }

    private void installEscToBack(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                onBack();
                event.consume();
            }
        });
    }

    private void focusCode() {
        Platform.runLater(() -> {
            if (codeField.getScene() != null) {
                codeField.requestFocus();
                codeField.selectAll();
            }
        });
    }
}
