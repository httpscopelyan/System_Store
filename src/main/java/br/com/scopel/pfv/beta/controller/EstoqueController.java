package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.BulkCellEditor;
import br.com.scopel.pfv.beta.controller.support.BulkEditConfirmation;
import br.com.scopel.pfv.beta.controller.support.ViewOnlyMode;
import br.com.scopel.pfv.beta.controller.support.ManagerAuthorizedAction;
import br.com.scopel.pfv.beta.controller.support.ProductSelection;
import br.com.scopel.pfv.beta.controller.support.ProductTablePresenter;
import br.com.scopel.pfv.beta.controller.support.TableEditKeys;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.StaffPermissions;
import br.com.scopel.pfv.beta.service.PricingSuggestionService;
import br.com.scopel.pfv.beta.service.ProductBulkEditService;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
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
    @FXML private CheckBox inactiveOnlyBox;
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
                pricingSuggestionService, selection, manager, StaffPermissions.canEditStockQuantity(user),
                this::onCellEdited);
        managerAction = new ManagerAuthorizedAction(managerAuthorizationController, userSession, this::window);
        bulkCellEditor = new BulkCellEditor(bulkEditService, selection, managerAction, BulkEditConfirmation::ask);
        ViewOnlyMode.apply(user, table);
        marginsToggle.visibleProperty().set(manager);
        marginsToggle.managedProperty().bind(marginsToggle.visibleProperty());
        marginsToggle.setSelected(manager);
        acceptSuggestionButton.setDisable(true);
        table.getSelectionModel().getSelectedCells().addListener(
                (ListChangeListener<TablePosition>) change -> refreshAcceptSuggestion());
        TableEditKeys.install(table);
        reload();
        Platform.runLater(() -> codeFilter.requestFocus());
    }

    @FXML
    public void onFilter() { reload(); }

    @FXML
    public void onClear() {
        codeFilter.clear();
        nameFilter.clear();
        belowMinimumBox.setSelected(false);
        withoutCostBox.setSelected(false);
        inactiveOnlyBox.setSelected(false);
        reload();
        codeFilter.requestFocus();
    }

    @FXML
    public void onNavigate(ActionEvent event) {
        navigator.navigate(Page.valueOf((String) ((Node) event.getSource()).getUserData()));
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
        boolean confirmed = selected != null && BulkEditConfirmation.ask("Apagar produto",
                "Apagar \"" + selected.getDescription() + "\"? Ele sai da venda; o histórico é mantido.");
        if (!confirmed) {
            return;
        }
        Product deleted = managerAction.apply(actor -> productService.inactivate(selected.getCode(), actor));
        reload();
        messageLabel.setText(deleted == null ? "Exclusão não autorizada." : "Produto apagado (inativo).");
    }

    @FXML
    public void onAcceptSuggestion() {
        finishEdit(bulkCellEditor.acceptSuggestion(
                selection.idsInColumn(presenter.priceColumn()), presenter.priceColumn()));
    }

    @FXML
    public void onToggleMargins() {
        presenter.showMargins(marginsToggle.isSelected());
    }

    private void reload() {
        List<Product> products = productService.filterForStock(codeFilter.getText(), nameFilter.getText(),
                belowMinimumBox.isSelected(), withoutCostBox.isSelected(),
                inactiveOnlyBox.isSelected());
        presenter.show(products);
        messageLabel.setText(products.size() + " produto(s)");
    }

    private void onCellEdited(ProductTablePresenter.CellEdit edit) {
        Platform.runLater(() -> finishEdit(bulkCellEditor.commit(edit)));
    }

    private void finishEdit(BulkCellEditor.Outcome outcome) {
        reload();
        if (outcome.applied()) {
            selection.restore(outcome.productIds(), outcome.tableColumn());
        }
        messageLabel.setText(outcome.message());
    }

    private void refreshAcceptSuggestion() {
        acceptSuggestionButton.setDisable(selection.selectedColumn() != presenter.priceColumn());
    }

    private Product singleSelectedProduct(String verb) {
        int rows = selection.rowCount();
        if (rows != 1) {
            messageLabel.setText((rows == 0 ? "Selecione um produto para " : "Selecione apenas um produto para ")
                    + verb + ".");
            return null;
        }
        return selection.firstProduct();
    }

    private Window window() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }
}
