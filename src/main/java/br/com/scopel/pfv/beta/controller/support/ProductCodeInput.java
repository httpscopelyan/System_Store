package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.service.ProductService;
import javafx.scene.control.TextField;

public final class ProductCodeInput {

    private static final int SUGGESTION_LIMIT = 8;

    private ProductCodeInput() {
    }

    public static void attach(TextField field, ProductService productService) {
        field.setPromptText("Código ou nome do produto");
        ProductSuggestionPopup[] popup = new ProductSuggestionPopup[1];
        popup[0] = new ProductSuggestionPopup(field,
                term -> productService.searchForSale(term, SUGGESTION_LIMIT),
                product -> {
                    popup[0].setTextSilently(product.getCode());
                    popup[0].dismiss();
                    field.positionCaret(field.getLength());
                });
    }
}
