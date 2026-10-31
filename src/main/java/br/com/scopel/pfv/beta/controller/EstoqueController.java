package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.BulkCellEditor;
import br.com.scopel.pfv.beta.controller.support.ManagerAuthorizedAction;
import br.com.scopel.pfv.beta.controller.support.ProductSelection;
import br.com.scopel.pfv.beta.controller.support.ProductTablePresenter;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.PricingSuggestionService;
import br.com.scopel.pfv.beta.service.ProductBulkEditService;
import br.com.scopel.pfv.beta.service.ProductBulkEditService.BulkResult;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EstoqueController {

    private final ProductService productService;
    private final ProductBulkEditService bulkEditService;
    private final PricingSuggestionService pricingSuggestionService;
    private final Navigator navigator;
    private final UserSession userSession;
    private final ManagerAuthorizationController managerAuthorizationController;

    private ProductSelection selection;
    private ProductTablePresenter presenter;
    private BulkCellEditor bulkCellEditor;
    private ManagerAuthorizedAction managerAction;

    @FXML private TextField codeFilter;
    @FXML private TextField nameFilter;
    @FXML private CheckBox belowMinimumBox;
    @FXML private CheckBox withoutCostBox;
    @FXML private TableView<Product> table;
    @FXML private TableColumn<Product, String> colHandle;
    @FXML private TableColumn<Product, String> colCode;
    @FXML private TableColumn<Product, String> colDesc;
    @FXML private TableColumn<Product, String> colCost;
    @FXML private TableColumn<Product, String> colProfit;
    @FXML private TableColumn<Product, String> colMarginPercent;
    @FXML private TableColumn<Product, String> colSuggestedPrice;
    @FXML private TableColumn<Product, String> colPrice;
    @FXML private TableColumn<Product, String> colMin;
    @FXML private TableColumn<Product, String> colStock;
    @FXML private Button acceptSuggestionButton;
    @FXML private ToggleButton marginsToggle;
    @FXML private Label messageLabel;

    public EstoqueController(ProductService productService, ProductBulkEditService bulkEditService,
                             PricingSuggestionService pricingSuggestionService, Navigator navigator,
                             UserSession userSession,
                             ManagerAuthorizationController managerAuthorizationController) {
        this.productService = productService;
        this.bulkEditService = bulkEditService;
        this.pricingSuggestionService = pricingSuggestionService;
        this.navigator = navigator;
        this.userSession = userSession;
        this.managerAuthorizationController = managerAuthorizationController;
    }

    @FXML
    public void initialize() {
        User user = userSession.getLoggedUser();
        boolean manager = user != null && user.getRole() == User.Role.GERENTE;
        selection = new ProductSelection(table);
        presenter = new ProductTablePresenter(table,
                new ProductTablePresenter.Columns(colHandle, colCode, colDesc, colCost, colProfit,
                        colMarginPercent, colSuggestedPrice, colPrice, colMin, colStock),
                pricingSuggestionService, selection, manager, this::onCellEdited);
        managerAction = new ManagerAuthorizedAction(managerAuthorizationController, userSession, this::window);
        bulkCellEditor = new BulkCellEditor(bulkEditService, selection, managerAction, this::confirm);
        marginsToggle.setVisible(manager);
        marginsToggle.setManaged(manager);
        marginsToggle.setSelected(manager);
        acceptSuggestionButton.setDisable(true);
        table.getSelectionModel().getSelectedCells().addListener(
                (ListChangeListener<TablePosition>) change -> refreshAcceptSuggestion());
        table.addEventFilter(KeyEvent.KEY_PRESSED, this::startEditOnEnter);
        reload();
        Platform.runLater(() -> codeFilter.requestFocus());
    }

    @FXML
    public void onFilter() {
        reload();
    }

    @FXML
    public void onClear() {
        codeFilter.clear();
        nameFilter.clear();
        belowMinimumBox.setSelected(false);
        withoutCostBox.setSelected(false);
        reload();
        codeFilter.requestFocus();
    }

    @FXML
    public void onRefresh() {
        reload();
    }

    @FXML
    public void onNew() {
        navigator.navigate(Page.PRODUTOS);
    }

    @FXML
    public void onAlterar() {
        Product selected = singleSelectedProduct("alterar");
        if (selected == null) {
            return;
        }
        userSession.setProdutoEmEdicaoId(selected.getId());
        navigator.navigate(Page.EDITAR_PRODUTO);
    }

    @FXML
    public void onApagar() {
        Product selected = singleSelectedProduct("inativar");
        if (selected == null) {
            return;
        }
        productService.inactivate(selected.getCode());
        reload();
        messageLabel.setText("Produto " + selected.getCode() + " inativado.");
    }

    @FXML
    public void onAcceptSuggestion() {
        List<Long> ids = selection.idsInColumn(presenter.priceColumn());
        if (ids.isEmpty()) {
            messageLabel.setText("Selecione células da coluna Vr. Venda.");
            return;
        }
        BulkResult result = managerAction.apply(actor -> bulkEditService.applySuggestedPrice(ids, actor));
        if (result == null) {
            messageLabel.setText("Alteração não autorizada.");
            return;
        }
        reload();
        selection.restore(ids, presenter.priceColumn());
        String skipped = result.skipped() > 0 ? " (" + result.skipped() + " sem custo)" : "";
        messageLabel.setText("Sugestão aplicada em " + result.changed() + " de " + ids.size() + skipped);
    }

    @FXML
    public void onToggleMargins() {
        presenter.showMargins(marginsToggle.isSelected());
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

    @FXML
    public void onListaCompras() {
        navigator.navigate(Page.LISTA_COMPRAS);
    }

    private void reload() {
        List<Product> products = productService.filterForStock(codeFilter.getText(), nameFilter.getText(),
                belowMinimumBox.isSelected(), withoutCostBox.isSelected());
        presenter.show(products);
        messageLabel.setText(products.size() + " produto(s)");
    }

    private void onCellEdited(ProductTablePresenter.CellEdit edit) {
        Platform.runLater(() -> {
            BulkCellEditor.Outcome outcome = bulkCellEditor.commit(edit);
            reload();
            if (outcome.applied()) {
                selection.restore(outcome.productIds(), outcome.tableColumn());
            }
            messageLabel.setText(outcome.message());
        });
    }

    private void startEditOnEnter(KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER && event.getCode() != KeyCode.F2) {
            return;
        }
        TablePosition<Product, ?> focused = table.getFocusModel().getFocusedCell();
        if (focused == null || focused.getRow() < 0 || focused.getTableColumn() == null
                || !focused.getTableColumn().isEditable() || table.getEditingCell() != null) {
            return;
        }
        table.edit(focused.getRow(), focused.getTableColumn());
        event.consume();
    }

    private void refreshAcceptSuggestion() {
        acceptSuggestionButton.setDisable(selection.selectedColumn() != presenter.priceColumn());
    }

    private Product singleSelectedProduct(String verb) {
        int rows = selection.rowCount();
        if (rows == 0) {
            messageLabel.setText("Selecione um produto para " + verb + ".");
            return null;
        }
        if (rows > 1) {
            messageLabel.setText("Selecione apenas um produto para " + verb + ".");
            return null;
        }
        return selection.firstProduct();
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText("Edição em lote");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private Window window() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }
}
