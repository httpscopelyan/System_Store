package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InvalidDiscountException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.exception.SaleItemNotFoundException;
import br.com.scopel.pfv.beta.model.Customer;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.service.CustomerService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SaleController {

    private final SaleService saleService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final CustomerService customerService;

    private Sale vendaAtual;

    private final ObservableList<SaleItemRow> rows = FXCollections.observableArrayList();

    @FXML private TextField codeField;
    @FXML private TableView<SaleItemRow> itemsTable;
    @FXML private TableColumn<SaleItemRow, String> descriptionCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> quantityCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> unitPriceCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> totalPriceCol;
    @FXML private Label totalLabel;
    @FXML private Label statusLabel;

    // Controles de Venda+ (Inc 4). Opcionais no FXML.
    @FXML private TextField discountField;
    @FXML private TextField customerIdField;
    @FXML private TextField cancelItemCodeField;
    @FXML private TextField cancelReasonField;

    public SaleController(SaleService saleService, UserSession userSession, Navigator navigator,
                          CustomerService customerService) {
        this.saleService = saleService;
        this.userSession = userSession;
        this.navigator = navigator;
        this.customerService = customerService;
    }

    @FXML
    public void initialize() {
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        unitPriceCol.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        totalPriceCol.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));

        itemsTable.setItems(rows);
        // O leitor age como teclado: a tabela não deve roubar o foco do código.
        itemsTable.setFocusTraversable(false);

        // RNF03/RNF07: o campo de código fica SEMPRE com o foco. Se perder (clique
        // na tabela, etc.), recupera — para o bipe sempre cair no campo certo.
        codeField.focusedProperty().addListener((obs, was, is) -> {
            if (!is && !codeField.isDisabled() && codeField.getScene() != null) {
                focusCode();
            }
        });

        // RN14: não há venda sem caixa aberto. Em vez de redirecionar, avisa aqui.
        if (userSession.getCaixaAtual() == null) {
            vendaAtual = null;
            codeField.setDisable(true);
            statusLabel.setText("Abra o caixa antes de vender");
            return;
        }

        codeField.setDisable(false);
        statusLabel.setText("");
        vendaAtual = saleService.openSale(
                userSession.getLoggedUser(),
                userSession.getCaixaAtual()
        );

        focusCode();
    }

    /** Põe o foco no campo de código e seleciona o conteúdo (bipe pronto). */
    private void focusCode() {
        // No setCenter o nó pode ainda não estar na cena; foca no próximo pulso.
        Platform.runLater(() -> {
            if (codeField.getScene() != null && !codeField.isDisabled()) {
                codeField.requestFocus();
                codeField.selectAll();
            }
        });
    }

    @FXML
    public void onAddItem() {
        if (vendaAtual == null) return;
        try {

            String code = codeField.getText();
            vendaAtual = saleService.addItem(vendaAtual, code, BigDecimal.ONE);

            refreshTable();
            codeField.clear();
            codeField.requestFocus();

        } catch (ProductNotfindException e) {
            statusLabel.setText("Produto não encontrado");
            codeField.clear();
            codeField.requestFocus();
        }
    }

    @FXML
    public void onFinalize() {
        if (vendaAtual == null) return;
        try {
            vendaAtual = saleService.goToPayment(vendaAtual);
            userSession.setVendaAtual(vendaAtual);

            navigator.navigate(Page.PAGAMENTO);

        } catch (IllegalSaleStateException e) {
            statusLabel.setText("Adicione itens antes de finalizar");
        }
    }

    @FXML
    public void onApplyDiscount() {
        if (vendaAtual == null) return;
        try {
            BigDecimal discount = new BigDecimal(discountField.getText().trim().replace(',', '.'));
            vendaAtual = saleService.applySaleDiscount(vendaAtual, discount, userSession.getLoggedUser());
            refreshTable();
            statusLabel.setText("Desconto aplicado");
        } catch (NumberFormatException e) {
            statusLabel.setText("Desconto inválido");
        } catch (ManagerAuthorizationRequiredException e) {
            statusLabel.setText("Desconto acima do limite: requer gerente");
        } catch (InvalidDiscountException e) {
            statusLabel.setText(e.getMessage());
        }
    }

    @FXML
    public void onSetCustomer() {
        if (vendaAtual == null) return;
        try {
            Customer customer = customerService.findById(Long.parseLong(customerIdField.getText().trim()));
            vendaAtual = saleService.setCustomer(vendaAtual, customer);
            statusLabel.setText("Cliente: " + customer.getName());
        } catch (NumberFormatException e) {
            statusLabel.setText("ID de cliente inválido");
        } catch (Exception e) {
            statusLabel.setText("Cliente não encontrado");
        }
    }

    @FXML
    public void onCancelItem() {
        if (vendaAtual == null) return;
        try {
            vendaAtual = saleService.cancelItem(vendaAtual, cancelItemCodeField.getText().trim(),
                    userSession.getLoggedUser());
            cancelItemCodeField.clear();
            refreshTable();
            statusLabel.setText("Item cancelado");
        } catch (ManagerAuthorizationRequiredException e) {
            statusLabel.setText("Cancelar item requer gerente");
        } catch (SaleItemNotFoundException | ProductNotfindException e) {
            statusLabel.setText("Item não está na venda");
        }
    }

    @FXML
    public void onCancelSale() {
        if (vendaAtual == null) return;
        try {
            saleService.cancelSale(vendaAtual, cancelReasonField.getText(), userSession.getLoggedUser());
            cancelReasonField.clear();
            statusLabel.setText("Venda cancelada");
            // Abre nova venda para continuar operando.
            vendaAtual = saleService.openSale(userSession.getLoggedUser(), userSession.getCaixaAtual());
            refreshTable();
            focusCode();
        } catch (ManagerAuthorizationRequiredException e) {
            statusLabel.setText("Cancelar venda requer gerente");
        } catch (IllegalSaleStateException e) {
            statusLabel.setText("Não é possível cancelar nesta situação");
        }
    }

    private void refreshTable() {
        rows.clear();
        for (SaleItem item : vendaAtual.getItems()) {
            rows.add(new SaleItemRow(
                    item.getProduct().getDescription(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTotalPrice()
            ));
        }
        totalLabel.setText("R$ " + vendaAtual.getTotal());
    }
}