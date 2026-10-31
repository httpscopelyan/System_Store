package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.ActiveSale;
import br.com.scopel.pfv.beta.controller.support.ManagerAuthorizedAction;
import br.com.scopel.pfv.beta.controller.support.SaleAdjustments;
import br.com.scopel.pfv.beta.controller.support.SaleFocusGuard;
import br.com.scopel.pfv.beta.controller.support.SaleScanFlow;
import br.com.scopel.pfv.beta.controller.support.SaleView;
import br.com.scopel.pfv.beta.controller.support.SceneKeyShortcuts;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.CustomerService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@Component
public class SaleController {

    private final SaleService saleService;
    private final ProductService productService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final CustomerService customerService;
    private final ReceiptService receiptService;
    private final ManagerAuthorizationController managerAuthorizationController;
    private final QuickProductController quickProductController;

    private ActiveSale activeSale;
    private SaleView view;
    private SaleFocusGuard focusGuard;
    private SaleScanFlow scanFlow;
    private SaleAdjustments adjustments;

    @FXML private TextField codeField;
    @FXML private TextField quantityField;
    @FXML private Label quantityLabel;
    @FXML private Label quantityPreviewLabel;
    @FXML private TableView<SaleItemRow> itemsTable;
    @FXML private TableColumn<SaleItemRow, String> descriptionCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> quantityCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> unitPriceCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> totalPriceCol;
    @FXML private Label totalLabel;
    @FXML private Label statusLabel;
    @FXML private Label lastItemLabel;
    @FXML private TextArea receiptPreview;
    @FXML private TextField discountField;
    @FXML private TextField customerIdField;
    @FXML private TextField cancelItemCodeField;
    @FXML private TextField cancelReasonField;

    public SaleController(SaleService saleService, ProductService productService, UserSession userSession,
                          Navigator navigator, CustomerService customerService, ReceiptService receiptService,
                          ManagerAuthorizationController managerAuthorizationController,
                          QuickProductController quickProductController) {
        this.saleService = saleService;
        this.productService = productService;
        this.userSession = userSession;
        this.navigator = navigator;
        this.customerService = customerService;
        this.receiptService = receiptService;
        this.managerAuthorizationController = managerAuthorizationController;
        this.quickProductController = quickProductController;
    }

    @FXML
    public void initialize() {
        Sale sessionSale = userSession.getVendaAtual();

        activeSale = new ActiveSale(userSession);
        view = new SaleView(itemsTable, descriptionCol, quantityCol, unitPriceCol, totalPriceCol,
                totalLabel, statusLabel, lastItemLabel, receiptPreview, receiptService);
        view.reset();
        codeField.clear();
        focusGuard = new SaleFocusGuard(codeField, Set.<Node>of(itemsTable));
        scanFlow = new SaleScanFlow(activeSale, view, productService, saleService, quickProductController,
                codeField, quantityField, quantityLabel, quantityPreviewLabel, focusGuard);
        scanFlow.attachSuggestions();
        adjustments = new SaleAdjustments(activeSale, view, saleService, customerService, userSession,
                new ManagerAuthorizedAction(managerAuthorizationController, userSession, view::window),
                discountField, customerIdField, cancelItemCodeField, cancelReasonField);

        for (TextField field : new TextField[]{discountField, customerIdField, cancelItemCodeField,
                cancelReasonField}) {
            field.clear();
            field.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    focusGuard.focusScanField();
                    event.consume();
                }
            });
        }
        quantityField.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                scanFlow.cancelPending();
                event.consume();
            }
        });
        SceneKeyShortcuts.install(statusLabel, Map.of(
                KeyCode.F5, this::onApplyDiscount,
                KeyCode.F6, this::onCancelItem,
                KeyCode.F7, this::onCancelSale,
                KeyCode.F9, this::onSetCustomer,
                KeyCode.F10, this::onFinalize));

        if (userSession.getCaixaAtual() == null) {
            activeSale.set(null);
            codeField.setDisable(true);
            view.showStatus("Abra o caixa antes de vender");
            return;
        }

        codeField.setDisable(false);
        Sale sale = saleService.resumeOrOpenSale(userSession.getLoggedUser(), userSession.getCaixaAtual(),
                sessionSale);
        activeSale.set(sale);

        if (sale.getStatus() == Sale.Status.EM_PAGAMENTO) {
            Platform.runLater(() -> navigator.navigate(Page.PAGAMENTO));
            return;
        }
        view.showSale(sale);
        view.showLastItem(view.lastItemOf(sale));
        focusGuard.focusScanField();
    }

    @FXML
    public void onAddItem() {
        scanFlow.onScan();
    }

    @FXML
    public void onQuantityEntered() {
        scanFlow.onQuantityConfirmed();
    }

    @FXML
    public void onFinalize() {
        if (!activeSale.exists()) {
            view.showStatus("Nenhuma venda em andamento");
            return;
        }
        try {
            activeSale.set(saleService.goToPayment(activeSale.current()));
            navigator.navigate(Page.PAGAMENTO);
        } catch (IllegalSaleStateException noItems) {
            view.showStatus("Adicione itens antes de finalizar");
            focusGuard.focusScanField();
        }
    }

    @FXML
    public void onApplyDiscount() {
        runThenFocusScan(adjustments::applyDiscount);
    }

    @FXML
    public void onSetCustomer() {
        runThenFocusScan(adjustments::linkCustomer);
    }

    @FXML
    public void onCancelItem() {
        runThenFocusScan(adjustments::cancelItem);
    }

    @FXML
    public void onCancelSale() {
        runThenFocusScan(adjustments::cancelSale);
    }

    private void runThenFocusScan(Runnable action) {
        try {
            action.run();
        } finally {
            focusGuard.focusScanField();
        }
    }
}
