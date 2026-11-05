package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.ThemeManager;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.ManagerAuthorizationController;
import br.com.scopel.pfv.beta.controller.support.BulkCellEditor;
import br.com.scopel.pfv.beta.controller.support.ManagerAuthorizedAction;
import br.com.scopel.pfv.beta.controller.support.ProductSelection;
import br.com.scopel.pfv.beta.controller.support.ProductTablePresenter;
import br.com.scopel.pfv.beta.controller.support.ProductTablePresenter.CellEdit;
import br.com.scopel.pfv.beta.controller.support.ProductTablePresenter.Columns;
import br.com.scopel.pfv.beta.controller.support.ProductTablePresenter.EditableColumn;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.PricingSuggestionService;
import br.com.scopel.pfv.beta.service.ProductBulkEditService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.UserService;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.awt.GraphicsEnvironment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@DisabledIf("isHeadless")
class BulkCellEditorTests {

    @Autowired private ProductBulkEditService bulkEditService;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private PricingSuggestionService pricingSuggestionService;
    @Autowired private UserSession userSession;
    @Autowired private UserService userService;
    @Autowired private ThemeManager themeManager;

    private TableView<Product> table;
    private Columns columns;
    private ProductSelection selection;
    private ProductTablePresenter presenter;
    private final List<String> confirmations = new ArrayList<>();
    private boolean confirmAnswer = true;
    private Optional<User> dialogAnswer;
    private Product a;
    private Product b;
    private Product c;
    private Product noCost;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void prepare() {
        User manager = new User();
        manager.setRole(User.Role.GERENTE);
        userSession.setLoggedUser(manager);
        dialogAnswer = Optional.of(manager);
        a = product("Monster A", "10.00", "7.00");
        b = product("Monster B", "10.00", "8.00");
        c = product("Monster C", "10.00", "5.00");
        noCost = product("Sem custo", "10.00", "0.00");

        table = new TableView<>();
        columns = new Columns(column("h"), column("code"), column("desc"), column("cost"), column("profit"),
                column("margin"), column("suggested"), column("price"), column("min"), column("stock"));
        table.getColumns().addAll(List.of(columns.handle(), columns.code(), columns.description(),
                columns.cost(), columns.profit(), columns.marginPercent(), columns.suggestedPrice(),
                columns.price(), columns.minStock(), columns.stock()));
        selection = new ProductSelection(table);
        presenter = new ProductTablePresenter(table, columns, pricingSuggestionService, selection, true, edit -> {
        });
        presenter.show(List.of(a, b, c, noCost));
    }

    private TableColumn<Product, String> column(String name) {
        return new TableColumn<>(name);
    }

    private Product product(String description, String price, String cost) {
        return productService.create("cell " + UUID.randomUUID().toString().substring(0, 8), description,
                Product.Unidade.UN, new BigDecimal(price), new BigDecimal(cost), new BigDecimal("10"),
                BigDecimal.ZERO, null, true);
    }

    private BulkCellEditor editor() {
        ManagerAuthorizationController dialog = new ManagerAuthorizationController(userService, themeManager) {
            @Override
            public Optional<User> requestAuthorization(Window ownerWindow) {
                return dialogAnswer;
            }
        };
        ManagerAuthorizedAction action = new ManagerAuthorizedAction(dialog, userSession, () -> null);
        return new BulkCellEditor(bulkEditService, selection, action, message -> {
            confirmations.add(message);
            return confirmAnswer;
        });
    }

    private CellEdit edit(EditableColumn kind, TableColumn<Product, String> column, Product product, String text) {
        return new CellEdit(kind, column, product, text);
    }

    private Product reload(Product product) {
        return productRepository.findById(product.getId()).orElseThrow();
    }

    @Test
    void shiftSelectingPriceCellsThenEditingOneAppliesToAllAfterConfirmation_RF61() {
        table.getSelectionModel().selectRange(0, columns.price(), 2, columns.price());
        BulkCellEditor.Outcome outcome = editor().commit(edit(EditableColumn.PRICE, columns.price(), a, "4,50"));
        assertTrue(outcome.applied());
        assertEquals(List.of("Aplicar R$ 4,50 em 3 produtos?"), confirmations);
        assertEquals(new BigDecimal("4.50"), reload(a).getPrice());
        assertEquals(new BigDecimal("4.50"), reload(b).getPrice());
        assertEquals(new BigDecimal("4.50"), reload(c).getPrice());
        assertEquals(new BigDecimal("10.00"), reload(noCost).getPrice());
    }

    @Test
    void ctrlSelectingIndividualCellsEditsOnlyThose_RF61() {
        table.getSelectionModel().select(0, columns.price());
        table.getSelectionModel().select(2, columns.price());
        editor().commit(edit(EditableColumn.PRICE, columns.price(), a, "6"));
        assertEquals(new BigDecimal("6.00"), reload(a).getPrice());
        assertEquals(new BigDecimal("10.00"), reload(b).getPrice());
        assertEquals(new BigDecimal("6.00"), reload(c).getPrice());
    }

    @Test
    void declinedConfirmationChangesNothing_RF61() {
        confirmAnswer = false;
        table.getSelectionModel().selectRange(0, columns.price(), 2, columns.price());
        BulkCellEditor.Outcome outcome = editor().commit(edit(EditableColumn.PRICE, columns.price(), a, "1"));
        assertFalse(outcome.applied());
        assertEquals(new BigDecimal("10.00"), reload(a).getPrice());
    }

    @Test
    void singleCellEditNeedsNoConfirmation_RF61() {
        table.getSelectionModel().select(1, columns.price());
        BulkCellEditor.Outcome outcome = editor().commit(edit(EditableColumn.PRICE, columns.price(), b, "7.77"));
        assertTrue(outcome.applied());
        assertTrue(confirmations.isEmpty());
        assertEquals(new BigDecimal("7.77"), reload(b).getPrice());
        assertEquals(new BigDecimal("10.00"), reload(a).getPrice());
    }

    @Test
    void editingACellOutsideTheSelectionChangesOnlyThatRow_RF61() {
        table.getSelectionModel().selectRange(0, columns.price(), 1, columns.price());
        editor().commit(edit(EditableColumn.PRICE, columns.price(), c, "2"));
        assertEquals(new BigDecimal("2.00"), reload(c).getPrice());
        assertEquals(new BigDecimal("10.00"), reload(a).getPrice());
        assertTrue(confirmations.isEmpty());
    }

    @Test
    void nameEditNeverSpreadsToOtherSelectedRows_RN26() {
        table.getSelectionModel().selectRange(0, columns.description(), 2, columns.description());
        editor().commit(edit(EditableColumn.NAME, columns.description(), a, "Monster Zero"));
        assertEquals("Monster Zero", reload(a).getDescription());
        assertEquals("Monster B", reload(b).getDescription());
        assertTrue(confirmations.isEmpty());
    }

    @Test
    void marginEditRecomputesEachRowFromItsOwnCostAndReportsSkippedOnes_RN26() {
        table.getSelectionModel().selectRange(0, columns.marginPercent(), 3, columns.marginPercent());
        BulkCellEditor.Outcome outcome = editor().commit(edit(EditableColumn.MARGIN_PERCENT, columns.marginPercent(), a, "50%"));
        assertTrue(outcome.applied());
        assertEquals(new BigDecimal("10.50"), reload(a).getPrice());
        assertEquals(new BigDecimal("12.00"), reload(b).getPrice());
        assertEquals(new BigDecimal("7.50"), reload(c).getPrice());
        assertEquals(new BigDecimal("10.00"), reload(noCost).getPrice());
        assertTrue(outcome.message().contains("1 sem custo"));
    }

    @Test
    void minimumStockEditAppliesToTheSelection_RN26() {
        table.getSelectionModel().selectRange(0, columns.minStock(), 1, columns.minStock());
        editor().commit(edit(EditableColumn.MIN_STOCK, columns.minStock(), a, "12"));
        assertEquals(0, new BigDecimal("12").compareTo(reload(a).getMinStock()));
        assertEquals(0, new BigDecimal("12").compareTo(reload(b).getMinStock()));
        assertEquals(0, BigDecimal.ZERO.compareTo(reload(c).getMinStock()));
    }

    @Test
    void invalidNumberIsRejectedWithoutChangingAnything_RN26() {
        table.getSelectionModel().select(0, columns.price());
        BulkCellEditor.Outcome outcome = editor().commit(edit(EditableColumn.PRICE, columns.price(), a, "abc"));
        assertFalse(outcome.applied());
        assertTrue(outcome.message().startsWith("Valor inválido"));
        assertEquals(new BigDecimal("10.00"), reload(a).getPrice());
    }

    @Test
    void operatorPriceEditNeedsManagerAuthorizationAndIsRefusedWhenCancelled_RN26() {
        User operator = new User();
        operator.setRole(User.Role.OPERADOR);
        userSession.setLoggedUser(operator);
        dialogAnswer = Optional.empty();
        table.getSelectionModel().select(0, columns.price());
        BulkCellEditor.Outcome outcome = editor().commit(edit(EditableColumn.PRICE, columns.price(), a, "1"));
        assertFalse(outcome.applied());
        assertEquals("Alteração não autorizada.", outcome.message());
        assertEquals(new BigDecimal("10.00"), reload(a).getPrice());
    }

    @Test
    void operatorPriceEditSucceedsWhenAManagerAuthorizes_RN26() {
        User operator = new User();
        operator.setRole(User.Role.OPERADOR);
        userSession.setLoggedUser(operator);
        table.getSelectionModel().select(0, columns.price());
        BulkCellEditor.Outcome outcome = editor().commit(edit(EditableColumn.PRICE, columns.price(), a, "1"));
        assertTrue(outcome.applied());
        assertEquals(new BigDecimal("1.00"), reload(a).getPrice());
    }

    @Test
    void operatorMinimumStockEditNeedsNoManager_RN26() {
        User operator = new User();
        operator.setRole(User.Role.OPERADOR);
        userSession.setLoggedUser(operator);
        dialogAnswer = Optional.empty();
        table.getSelectionModel().select(0, columns.minStock());
        BulkCellEditor.Outcome outcome = editor().commit(edit(EditableColumn.MIN_STOCK, columns.minStock(), a, "4"));
        assertTrue(outcome.applied());
    }

    @Test
    void selectionReportsTheSingleColumnUsedByAcceptSuggestion_RF63() {
        table.getSelectionModel().selectRange(0, columns.price(), 2, columns.price());
        assertTrue(selection.isSingleColumn());
        assertTrue(presenter.isPriceColumn(selection.selectedColumn()));
        assertEquals(3, selection.idsInColumn(columns.price()).size());
    }

    @Test
    void selectionAcrossColumnsIsNotASingleColumn_RF63() {
        table.getSelectionModel().select(0, columns.price());
        table.getSelectionModel().select(0, columns.cost());
        assertFalse(selection.isSingleColumn());
        assertNull(selection.selectedColumn());
    }

    @Test
    void clickingTheRowHandleSelectsTheWholeProduct_RF62() {
        selection.selectWholeRow(1, false);
        assertEquals(1, selection.rowCount());
        assertEquals(b.getId(), selection.firstProduct().getId());
        assertEquals(10, selection.selectedColumns().size());
    }

    @Test
    void ctrlClickingHandlesAddsWholeRows_RF62() {
        selection.selectWholeRow(0, false);
        selection.selectWholeRow(2, true);
        assertEquals(2, selection.rowCount());
    }

    @Test
    void operatorDoesNotSeeCostProfitMarginAndSuggestionColumns_RF64() {
        TableView<Product> operatorTable = new TableView<>();
        Columns operatorColumns = new Columns(column("h"), column("code"), column("desc"), column("cost"),
                column("profit"), column("margin"), column("suggested"), column("price"), column("min"),
                column("stock"));
        operatorTable.getColumns().addAll(List.of(operatorColumns.handle(), operatorColumns.code(),
                operatorColumns.description(), operatorColumns.cost(), operatorColumns.profit(),
                operatorColumns.marginPercent(), operatorColumns.suggestedPrice(), operatorColumns.price(),
                operatorColumns.minStock(), operatorColumns.stock()));
        ProductTablePresenter operatorPresenter = new ProductTablePresenter(operatorTable, operatorColumns,
                pricingSuggestionService, new ProductSelection(operatorTable), false, edit -> {
        });
        assertTrue(operatorPresenter.managerOnlyColumns().stream().noneMatch(TableColumn::isVisible));
        assertTrue(operatorColumns.price().isVisible());
        assertTrue(columns.cost().isVisible());
    }

    @Test
    void stockQuantityColumnIsNeverEditable_RN26() {
        assertFalse(columns.stock().isEditable());
        assertTrue(columns.code().isEditable());
        assertTrue(columns.price().isEditable());
    }
}
