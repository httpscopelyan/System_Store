package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.PaymentInput;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.SplitPaymentResult;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.awt.GraphicsEnvironment;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class PaymentBackScreenTests {

    @Autowired private ApplicationContext context;
    @Autowired private UserSession userSession;
    @Autowired private Navigator navigator;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleService saleService;
    @Autowired private SplitPaymentService splitPaymentService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private JdbcTemplate jdbc;

    private Parent shellRoot;
    private Stage stage;
    private User operator;
    private Product product;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void openShellWithASaleReadyForPayment() throws Exception {
        User newOperator = new User();
        newOperator.setName("Operador voltar");
        newOperator.setLogin("back_" + UUID.randomUUID());
        newOperator.setPassword("x");
        newOperator.setRole(User.Role.OPERADOR);
        newOperator.setActive(true);
        operator = userRepository.save(newOperator);
        CashRegister register = cashRegisterService.open(operator, new BigDecimal("50.00"));
        product = new Product();
        product.setCode(UUID.randomUUID().toString().substring(0, 13));
        product.setDescription("Produto voltar");
        product.setPrice(new BigDecimal("30.00"));
        product.setQuantity(new BigDecimal("100.000"));
        product.setUnit(Product.Unidade.UN);
        product.setActive(true);
        productRepository.save(product);

        userSession.setLoggedUser(operator);
        userSession.setCaixaAtual(register);
        Sale sale = saleService.openSale(operator, register);
        sale = saleService.addItem(sale, product.getCode(), new BigDecimal("3"));
        userSession.setVendaAtual(saleService.goToPayment(sale));

        FxTestSupport.runOnFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_layout.fxml"));
                loader.setControllerFactory(context::getBean);
                shellRoot = loader.load();
                stage = new Stage();
                stage.setScene(new Scene(shellRoot, 1365, 720));
                stage.show();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
    }

    @AfterEach
    void cleanUp() throws Exception {
        FxTestSupport.runOnFx(() -> stage.close());
        userSession.setVendaAtual(null);
        userSession.setCaixaAtual(null);
        userSession.setLoggedUser(null);
        Long operatorId = operator.getId();
        String ownSales = "(select id from sale where operator_id = ?)";
        jdbc.update("delete from audit_log where actor_id = ?", operatorId);
        jdbc.update("delete from payment where sale_id in " + ownSales, operatorId);
        jdbc.update("delete from sale_item where sale_id in " + ownSales, operatorId);
        jdbc.update("delete from sale where operator_id = ?", operatorId);
        jdbc.update("delete from cash_register where operator_id = ?", operatorId);
        jdbc.update("delete from product where code = ?", product.getCode());
        jdbc.update("delete from users where id = ?", operatorId);
    }

    private void settle() throws Exception {
        for (int round = 0; round < 4; round++) {
            CompletableFuture<Void> tick = new CompletableFuture<>();
            Platform.runLater(() -> Platform.runLater(() -> tick.complete(null)));
            tick.get(10, TimeUnit.SECONDS);
        }
    }

    private Button shellBackButton() {
        return (Button) shellRoot.lookup("#backButton");
    }

    private Sale persistedSale() {
        return saleRepository.findById(userSession.getVendaAtual().getId()).orElseThrow();
    }

    private void openSaleScreenWhichResumesThePayment() throws Exception {
        FxTestSupport.runOnFx(() -> navigator.navigate(Page.VENDA));
        settle();
        assertEquals(Page.PAGAMENTO, navigator.getCurrentPage());
    }

    @Test
    void shellBackButtonIsEnabledOnThePaymentScreen() throws Exception {
        openSaleScreenWhichResumesThePayment();

        FxTestSupport.runOnFx(() -> assertFalse(shellBackButton().isDisabled()));
        assertTrue(navigator.canGoBack());
    }

    @Test
    void shellBackReopensTheSaleAndDoesNotBounceBackIntoPayment() throws Exception {
        openSaleScreenWhichResumesThePayment();

        FxTestSupport.runOnFx(() -> shellBackButton().fire());
        settle();

        assertEquals(Page.VENDA, navigator.getCurrentPage());
        assertEquals(Sale.Status.ABERTA, persistedSale().getStatus());
    }

    @Test
    void theVisibleBackButtonOfThePaymentScreenReturnsToTheSale() throws Exception {
        openSaleScreenWhichResumesThePayment();

        FxTestSupport.runOnFx(() -> {
            Button visibleBackButton = (Button) shellRoot.lookupAll(".button").stream()
                    .filter(node -> node instanceof Button button && "◀ Voltar (ESC)".equals(button.getText()))
                    .findFirst().orElseThrow();
            visibleBackButton.fire();
        });
        settle();

        assertEquals(Page.VENDA, navigator.getCurrentPage());
        assertEquals(Sale.Status.ABERTA, persistedSale().getStatus());
    }

    @Test
    void backIsRefusedWithAMessageWhileACardPartWasAlreadyReceived() throws Exception {
        SplitPaymentResult cardPart = splitPaymentService.registerPart(userSession.getVendaAtual(),
                PaymentInput.card(new BigDecimal("10.00"), CardType.DEBITO));
        userSession.setVendaAtual(cardPart.sale());
        openSaleScreenWhichResumesThePayment();

        FxTestSupport.runOnFx(() -> shellBackButton().fire());
        settle();

        assertEquals(Page.PAGAMENTO, navigator.getCurrentPage());
        assertEquals(Sale.Status.EM_PAGAMENTO, persistedSale().getStatus());
        FxTestSupport.runOnFx(() -> assertTrue(((Label) shellRoot.lookup("#statusLabel")).getText()
                .contains("não pode ser reaberta")));
    }

    @Test
    void backStaysRefusedWhileTheNavigatorIsLockedByALiveMachineCharge() throws Exception {
        openSaleScreenWhichResumesThePayment();
        navigator.lock("Cobrança em andamento na maquininha.");

        FxTestSupport.runOnFx(() -> shellBackButton().fire());
        settle();

        navigator.unlock();
        assertEquals(Page.PAGAMENTO, navigator.getCurrentPage());
        assertEquals(Sale.Status.EM_PAGAMENTO, persistedSale().getStatus());
    }
}
