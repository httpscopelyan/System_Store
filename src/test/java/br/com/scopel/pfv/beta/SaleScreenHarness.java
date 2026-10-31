package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ProductService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class SaleScreenHarness {

    private final ApplicationContext context;
    private final UserSession userSession;
    private final JdbcTemplate jdbc;
    private final ProductService productService;
    private final List<String> productCodes = new ArrayList<>();

    private User operator;
    private Stage stage;
    private Scene scene;
    private Parent root;

    private SaleScreenHarness(ApplicationContext context) {
        this.context = context;
        this.userSession = context.getBean(UserSession.class);
        this.jdbc = context.getBean(JdbcTemplate.class);
        this.productService = context.getBean(ProductService.class);
    }

    static SaleScreenHarness open(ApplicationContext context, User.Role role) throws Exception {
        SaleScreenHarness harness = new SaleScreenHarness(context);
        harness.prepareOperator(role);
        return harness;
    }

    private void prepareOperator(User.Role role) {
        User newOperator = new User();
        newOperator.setName("Operador de teste");
        newOperator.setLogin("fx_" + UUID.randomUUID());
        newOperator.setPassword("x");
        newOperator.setRole(role);
        newOperator.setActive(true);
        operator = context.getBean(UserRepository.class).save(newOperator);
        CashRegister cashRegister = context.getBean(CashRegisterService.class)
                .open(operator, new BigDecimal("50.00"));
        userSession.setLoggedUser(operator);
        userSession.setCaixaAtual(cashRegister);
        userSession.setVendaAtual(null);
    }

    String createProduct(String description, Product.Unidade unit, String price) {
        String code = "fx " + UUID.randomUUID().toString().substring(0, 8);
        productService.create(code, description, unit, new BigDecimal(price), new BigDecimal("1.00"),
                new BigDecimal("500"), BigDecimal.ZERO, null, true);
        productCodes.add(code);
        return code;
    }

    void showSaleScreen() throws Exception {
        onFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/sale.fxml"));
                loader.setControllerFactory(context::getBean);
                root = loader.load();
                scene = new Scene(root, 1200, 800);
                stage = new Stage();
                stage.setScene(scene);
                stage.show();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
        settle();
    }

    void close() throws Exception {
        onFx(() -> {
            for (Window window : new ArrayList<>(Window.getWindows())) {
                if (window instanceof Popup popup) {
                    popup.hide();
                }
            }
            if (stage != null) {
                stage.close();
            }
        });
        userSession.setVendaAtual(null);
        userSession.setCaixaAtual(null);
        Long operatorId = operator.getId();
        String ownSales = "(select id from sale where operator_id = ?)";
        jdbc.update("delete from payment where sale_id in " + ownSales, operatorId);
        jdbc.update("delete from sale_item where sale_id in " + ownSales, operatorId);
        jdbc.update("delete from sale where operator_id = ?", operatorId);
        jdbc.update("delete from cash_movement where cash_register_id in "
                + "(select id from cash_register where operator_id = ?)", operatorId);
        jdbc.update("delete from cash_register where operator_id = ?", operatorId);
        for (String code : productCodes) {
            jdbc.update("delete from product where code = ?", code);
        }
        jdbc.update("delete from users where id = ?", operatorId);
    }

    void onFx(Runnable action) throws Exception {
        FxTestSupport.runOnFx(action);
    }

    void settle() throws Exception {
        for (int round = 0; round < 3; round++) {
            CompletableFuture<Void> tick = new CompletableFuture<>();
            Platform.runLater(() -> Platform.runLater(() -> tick.complete(null)));
            tick.get(10, TimeUnit.SECONDS);
        }
    }

    void pause(long millis) throws Exception {
        Thread.sleep(millis);
        settle();
    }

    @SuppressWarnings("unchecked")
    <T extends Node> T node(String id) {
        Node found = root.lookup("#" + id);
        if (found == null) {
            throw new IllegalStateException("Nó não encontrado: " + id);
        }
        return (T) found;
    }

    String text(String id) throws Exception {
        CompletableFuture<String> value = new CompletableFuture<>();
        Platform.runLater(() -> {
            Node found = node(id);
            if (found instanceof TextInputControl input) {
                value.complete(input.getText());
            } else if (found instanceof Labeled labeled) {
                value.complete(labeled.getText());
            } else {
                value.completeExceptionally(new IllegalStateException("Sem texto: " + id));
            }
        });
        return value.get(10, TimeUnit.SECONDS);
    }

    boolean isDisabled(String id) throws Exception {
        CompletableFuture<Boolean> value = new CompletableFuture<>();
        Platform.runLater(() -> value.complete(node(id).isDisabled()));
        return value.get(10, TimeUnit.SECONDS);
    }

    Node focusOwner() throws Exception {
        CompletableFuture<Node> owner = new CompletableFuture<>();
        Platform.runLater(() -> owner.complete(scene.getFocusOwner()));
        return owner.get(10, TimeUnit.SECONDS);
    }

    void focus(String id) throws Exception {
        onFx(() -> node(id).requestFocus());
        settle();
    }

    void type(String id, String text) throws Exception {
        onFx(() -> ((TextInputControl) node(id)).setText(text));
        settle();
    }

    void submit(String id) throws Exception {
        onFx(() -> node(id).fireEvent(new ActionEvent()));
        settle();
    }

    void press(String id, KeyCode code) throws Exception {
        onFx(() -> {
            Node target = node(id);
            target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
        });
        settle();
    }

    int tableRows(String id) throws Exception {
        CompletableFuture<Integer> rows = new CompletableFuture<>();
        Platform.runLater(() -> rows.complete(((TableView<?>) node(id)).getItems().size()));
        return rows.get(10, TimeUnit.SECONDS);
    }

    Object firstRowValue(String tableId, String property) throws Exception {
        CompletableFuture<Object> value = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Object row = ((TableView<?>) node(tableId)).getItems().get(0);
                String getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
                value.complete(row.getClass().getMethod(getter).invoke(row));
            } catch (Exception failure) {
                value.completeExceptionally(failure);
            }
        });
        return value.get(10, TimeUnit.SECONDS);
    }

    boolean anyPopupShowing() throws Exception {
        CompletableFuture<Boolean> showing = new CompletableFuture<>();
        Platform.runLater(() -> showing.complete(Window.getWindows().stream()
                .anyMatch(window -> window instanceof Popup && window.isShowing())));
        return showing.get(10, TimeUnit.SECONDS);
    }

    Parent root() {
        return root;
    }
}
