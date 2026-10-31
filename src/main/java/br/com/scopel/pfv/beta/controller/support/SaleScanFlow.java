package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.controller.QuickProductController;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InvalidQuantityException;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public final class SaleScanFlow {

    private static final int SUGGESTION_LIMIT = 20;
    private static final String NO_SALE_MESSAGE = "Nenhuma venda em andamento";

    private final ActiveSale activeSale;
    private final SaleView view;
    private final ProductService productService;
    private final SaleService saleService;
    private final QuickProductController quickProductController;
    private final TextField codeField;
    private final TextField quantityField;
    private final Label quantityLabel;
    private final Label quantityPreviewLabel;
    private final SaleFocusGuard focusGuard;

    private ProductSuggestionPopup suggestionPopup;
    private PendingScan pending;

    public SaleScanFlow(ActiveSale activeSale, SaleView view, ProductService productService,
                        SaleService saleService, QuickProductController quickProductController,
                        TextField codeField, TextField quantityField, Label quantityLabel,
                        Label quantityPreviewLabel, SaleFocusGuard focusGuard) {
        this.activeSale = activeSale;
        this.view = view;
        this.productService = productService;
        this.saleService = saleService;
        this.quickProductController = quickProductController;
        this.codeField = codeField;
        this.quantityField = quantityField;
        this.quantityLabel = quantityLabel;
        this.quantityPreviewLabel = quantityPreviewLabel;
        this.focusGuard = focusGuard;
        quantityField.textProperty().addListener((observable, previous, current) -> refreshQuantityPreview());
        resetQuantityStep();
    }

    public void attachSuggestions() {
        suggestionPopup = new ProductSuggestionPopup(codeField,
                term -> productService.searchForSale(term, SUGGESTION_LIMIT), this::startQuantityStep);
    }

    public boolean hasPending() {
        return pending != null;
    }

    public void onScan() {
        if (!activeSale.exists()) {
            view.showStatus(NO_SALE_MESSAGE);
            return;
        }
        dismissSuggestions();
        String rawInput = codeField.getText() == null ? "" : codeField.getText().trim();
        if (rawInput.isEmpty()) {
            return;
        }
        try {
            ScanInputParser.ParsedScan parsed = ScanInputParser.parse(rawInput);
            if (parsed.hasQuantity()) {
                addItem(parsed.code(), parsed.quantity());
                return;
            }
            resolve(parsed.code());
        } catch (InvalidQuantityException invalidQuantity) {
            showError(invalidQuantity.getMessage());
        }
    }

    public void onQuantityConfirmed() {
        if (pending == null) {
            focusGuard.focusScanField();
            return;
        }
        BigDecimal quantity = DecimalInput.parseOrNull(quantityField.getText());
        if (quantity == null) {
            view.showStatus("Informe a quantidade");
            return;
        }
        addItem(pending.code(), quantity);
    }

    public void cancelPending() {
        if (pending == null) {
            return;
        }
        resetQuantityStep();
        view.showLastItem(view.lastItemOf(activeSale.current()));
        view.showStatus("");
        focusGuard.focusScanField();
    }

    private void resolve(String code) {
        try {
            startQuantityStep(productService.findCode(code));
        } catch (ProductNotfindException notExactCode) {
            List<Product> matches = productService.searchForSale(code, SUGGESTION_LIMIT);
            if (matches.isEmpty()) {
                offerQuickRegister(code);
                return;
            }
            if (matches.size() == 1) {
                startQuantityStep(matches.get(0));
                return;
            }
            view.showStatus("");
            if (suggestionPopup != null) {
                suggestionPopup.showResults(matches);
            }
        }
    }

    private void startQuantityStep(Product product) {
        if (!activeSale.exists()) {
            view.showStatus(NO_SALE_MESSAGE);
            return;
        }
        if (!saleService.asksQuantityAfterScan() && product.getUnit() == Product.Unidade.UN) {
            addItem(product.getCode(), BigDecimal.ONE);
            return;
        }
        pending = new PendingScan(product);
        showCode(product.getCode());
        QuantityInput.configure(quantityField, product.getUnit());
        quantityField.setDisable(false);
        quantityLabel.setText(pending.isWeight() ? "Peso (KG)" : "Qtd");
        quantityField.setPromptText(pending.isWeight() ? "Ex: 0,750" : "Qtd");
        quantityField.setText(pending.isWeight() ? "" : "1");
        view.showPending(product);
        view.showStatus("");
        Platform.runLater(() -> {
            quantityField.requestFocus();
            quantityField.selectAll();
        });
    }

    private void addItem(String code, BigDecimal quantity) {
        Sale sale = activeSale.current();
        try {
            Sale updated = saleService.addItem(sale, code, quantity);
            activeSale.set(updated);
            view.showSale(updated);
            view.showLastItem(view.itemByCode(updated, code));
            view.showStatus("");
            clearScan();
            focusGuard.focusScanField();
        } catch (ProductNotfindException productNotFound) {
            offerQuickRegister(code);
        } catch (InvalidQuantityException invalidQuantity) {
            view.showStatus(invalidQuantity.getMessage());
            if (pending != null) {
                Platform.runLater(() -> {
                    quantityField.requestFocus();
                    quantityField.selectAll();
                });
            } else {
                clearScan();
                focusGuard.focusScanField();
            }
        } catch (IllegalSaleStateException saleNotOpen) {
            showError("A venda não aceita novos itens");
        }
    }

    private void offerQuickRegister(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            showError("Produto não encontrado");
            return;
        }
        Optional<Product> registered = quickProductController.requestQuickRegister(view.window(), barcode);
        if (registered.isEmpty()) {
            showError("Produto não encontrado");
            return;
        }
        view.showStatus("Produto cadastrado — bipe novamente para vender");
        clearScan();
        focusGuard.focusScanField();
    }

    private void showError(String message) {
        view.showStatus(message);
        clearScan();
        focusGuard.focusScanField();
    }

    private void showCode(String code) {
        if (suggestionPopup != null) {
            suggestionPopup.setTextSilently(code);
        } else {
            codeField.setText(code);
        }
    }

    private void dismissSuggestions() {
        if (suggestionPopup != null) {
            suggestionPopup.dismiss();
        }
    }

    private void clearScan() {
        resetQuantityStep();
        dismissSuggestions();
        if (suggestionPopup != null) {
            suggestionPopup.setTextSilently("");
        } else {
            codeField.clear();
        }
    }

    private void resetQuantityStep() {
        pending = null;
        quantityField.setTextFormatter(null);
        quantityField.clear();
        quantityField.setDisable(true);
        quantityLabel.setText("Qtd");
        quantityField.setPromptText("Qtd");
        quantityPreviewLabel.setText("");
    }

    private void refreshQuantityPreview() {
        if (pending == null) {
            quantityPreviewLabel.setText("");
            return;
        }
        BigDecimal quantity = DecimalInput.parseOrNull(quantityField.getText());
        if (quantity == null || quantity.signum() <= 0) {
            quantityPreviewLabel.setText("");
            return;
        }
        BigDecimal unitPrice = pending.product().getPrice();
        String unitText = pending.isWeight() ? " kg" : "";
        quantityPreviewLabel.setText(MoneyFormat.quantity(quantity) + unitText + " × "
                + MoneyFormat.format(unitPrice) + " = "
                + MoneyFormat.format(saleService.previewLineTotal(unitPrice, quantity)));
    }
}
