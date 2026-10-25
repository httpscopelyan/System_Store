package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Customer;
import br.com.scopel.pfv.beta.model.Quote;
import br.com.scopel.pfv.beta.model.QuoteItem;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.CustomerService;
import br.com.scopel.pfv.beta.service.QuoteService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class QuoteController {

    private final QuoteService quoteService;
    private final CustomerService customerService;
    private final UserSession userSession;

    private Quote current;

    @FXML private TextField customerIdField;
    @FXML private DatePicker validUntilField;
    @FXML private Label statusLabel;
    @FXML private TextField codeField;
    @FXML private TextField qtyField;
    @FXML private TableView<QuoteItem> table;
    @FXML private TableColumn<QuoteItem, String> colProduct;
    @FXML private TableColumn<QuoteItem, String> colQty;
    @FXML private TableColumn<QuoteItem, String> colPrice;
    @FXML private TableColumn<QuoteItem, String> colTotal;
    @FXML private Label totalLabel;
    @FXML private Label messageLabel;

    public QuoteController(QuoteService quoteService, CustomerService customerService,
                          UserSession userSession) {
        this.quoteService = quoteService;
        this.customerService = customerService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        colProduct.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProduct().getDescription()));
        colQty.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getQuantity().toPlainString()));
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUnitPrice().toPlainString()));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTotalPrice().toPlainString()));
    }

    @FXML
    public void onOpen() {
        try {
            Customer customer = null;
            String idText = customerIdField.getText();
            if (idText != null && !idText.isBlank()) {
                customer = customerService.findById(Long.parseLong(idText.trim()));
            }
            current = quoteService.openQuote(customer, userSession.getLoggedUser(), validUntilField.getValue());
            statusLabel.setText("Orçamento #" + current.getId() + " aberto");
            refresh();
            messageLabel.setText("");
        } catch (NumberFormatException e) {
            messageLabel.setText("ID de cliente inválido");
        } catch (Exception e) {
            messageLabel.setText("Cliente não encontrado");
        }
    }

    @FXML
    public void onAddItem() {
        if (current == null) {
            messageLabel.setText("Abra um orçamento primeiro");
            return;
        }
        try {
            BigDecimal qty = new BigDecimal(qtyField.getText().trim().replace(',', '.'));
            current = quoteService.addItem(current, codeField.getText().trim(), qty);
            codeField.clear();
            qtyField.clear();
            refresh();
        } catch (NumberFormatException e) {
            messageLabel.setText("Quantidade inválida");
        } catch (Exception e) {
            messageLabel.setText("Produto não encontrado");
        }
    }

    @FXML
    public void onConvert() {
        if (current == null) {
            messageLabel.setText("Abra um orçamento primeiro");
            return;
        }
        if (userSession.getCaixaAtual() == null) {
            messageLabel.setText("Abra o caixa antes de converter em venda");
            return;
        }
        try {
            Sale sale = quoteService.convertToSale(current, userSession.getCaixaAtual(), userSession.getLoggedUser());
            userSession.setVendaAtual(sale);
            statusLabel.setText("Convertido na venda #" + sale.getId() + " (vá ao Pagamento)");
            messageLabel.setText("Orçamento convertido em venda");
            current = null;
            refresh();
        } catch (Exception e) {
            messageLabel.setText(e.getMessage());
        }
    }

    private void refresh() {
        if (current == null) {
            table.setItems(FXCollections.observableArrayList());
            totalLabel.setText("Total: R$ 0,00");
        } else {
            table.setItems(FXCollections.observableArrayList(current.getItems()));
            totalLabel.setText("Total: R$ " + current.getTotal().toPlainString());
        }
    }
}
