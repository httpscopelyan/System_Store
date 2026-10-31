package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.Product;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ProductSuggestionPopup {

    private static final int MIN_CHARACTERS = 2;
    private static final int RESULT_LIMIT = 20;
    private static final double ROW_HEIGHT = 34;
    private static final double MAX_HEIGHT = 340;

    private final TextField anchor;
    private final Function<String, List<Product>> searcher;
    private final Consumer<Product> onPick;
    private final Popup popup = new Popup();
    private final ListView<Product> list = new ListView<>();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(200));

    private boolean suppressed;

    public ProductSuggestionPopup(TextField anchor, Function<String, List<Product>> searcher,
                                  Consumer<Product> onPick) {
        this.anchor = anchor;
        this.searcher = searcher;
        this.onPick = onPick;
        configureList();
        popup.getContent().add(list);
        popup.setAutoFix(true);
        popup.setAutoHide(true);
        popup.setHideOnEscape(false);
        debounce.setOnFinished(event -> refresh());
        anchor.textProperty().addListener((observable, previous, current) -> onTextChanged(current));
        anchor.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        anchor.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused) {
                Platform.runLater(() -> {
                    if (anchor.getScene() == null || anchor.getScene().getFocusOwner() != anchor) {
                        popup.hide();
                    }
                });
            }
        });
    }

    public void showResults(List<Product> products) {
        if (products.isEmpty()) {
            popup.hide();
            return;
        }
        list.setItems(FXCollections.observableArrayList(products));
        list.getSelectionModel().clearSelection();
        if (anchor.getScene() == null || anchor.getScene().getWindow() == null) {
            return;
        }
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        list.setPrefWidth(Math.max(bounds.getWidth(), 420));
        list.setPrefHeight(Math.min(products.size() * ROW_HEIGHT + 4, MAX_HEIGHT));
        popup.show(anchor, bounds.getMinX(), bounds.getMaxY());
    }

    public void dismiss() {
        debounce.stop();
        popup.hide();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void setTextSilently(String text) {
        suppressed = true;
        try {
            anchor.setText(text);
        } finally {
            suppressed = false;
        }
    }

    private void configureList() {
        list.setFocusTraversable(false);
        list.getStyleClass().add("suggestion-list");
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Product product, boolean empty) {
                super.updateItem(product, empty);
                setText(empty || product == null ? null : describe(product));
            }
        });
        list.setOnMouseClicked(event -> {
            Product selected = list.getSelectionModel().getSelectedItem();
            if (event.getButton() == MouseButton.PRIMARY && selected != null) {
                pick(selected);
            }
        });
    }

    private String describe(Product product) {
        return product.getCode() + " — " + product.getDescription() + " — "
                + MoneyFormat.format(product.getPrice()) + " — " + product.getUnit() + " — "
                + MoneyFormat.quantity(product.getQuantity()) + " em estoque";
    }

    private void onTextChanged(String text) {
        if (suppressed) {
            return;
        }
        if (text == null || text.trim().length() < MIN_CHARACTERS) {
            dismiss();
            return;
        }
        debounce.playFromStart();
    }

    private void refresh() {
        String term = anchor.getText() == null ? "" : anchor.getText().trim();
        if (term.length() < MIN_CHARACTERS || anchor.getScene() == null
                || anchor.getScene().getFocusOwner() != anchor) {
            popup.hide();
            return;
        }
        showResults(searcher.apply(term).stream().limit(RESULT_LIMIT).toList());
    }

    private void onKey(KeyEvent event) {
        KeyCode code = event.getCode();
        if (!popup.isShowing()) {
            if (code == KeyCode.DOWN && anchor.getText() != null
                    && anchor.getText().trim().length() >= MIN_CHARACTERS) {
                refresh();
                selectRow(0);
                event.consume();
            }
            return;
        }
        switch (code) {
            case DOWN -> {
                selectRow(list.getSelectionModel().getSelectedIndex() + 1);
                event.consume();
            }
            case UP -> {
                selectRow(list.getSelectionModel().getSelectedIndex() - 1);
                event.consume();
            }
            case ENTER, TAB -> {
                Product selected = list.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    pick(selected);
                    event.consume();
                } else {
                    dismiss();
                }
            }
            case ESCAPE -> {
                dismiss();
                event.consume();
            }
            default -> {
            }
        }
    }

    private void selectRow(int index) {
        int last = list.getItems().size() - 1;
        if (last < 0) {
            return;
        }
        int bounded = Math.max(0, Math.min(index, last));
        list.getSelectionModel().select(bounded);
        list.scrollTo(bounded);
    }

    private void pick(Product product) {
        dismiss();
        onPick.accept(product);
    }
}
