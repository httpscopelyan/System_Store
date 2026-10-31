package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisabledIf("isHeadless")
class MainLayoutTests {

    @Autowired private ApplicationContext context;
    @Autowired private Navigator navigator;
    @Autowired private UserSession userSession;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private final List<Long> createdUsers = new ArrayList<>();

    private Parent root;
    private Scene scene;
    private Stage stage;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        FxTestSupport.startToolkit();
    }

    @BeforeEach
    void openShell() throws Exception {
        userSession.setLoggedUser(persistedUser(User.Role.GERENTE));
        userSession.setCaixaAtual(null);
        userSession.setVendaAtual(null);
        FxTestSupport.runOnFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_layout.fxml"));
                loader.setControllerFactory(context::getBean);
                root = loader.load();
                scene = new Scene(root, 1365, 720);
                stage = new Stage();
                stage.setScene(scene);
                stage.show();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
    }

    @AfterEach
    void closeShell() throws Exception {
        FxTestSupport.runOnFx(() -> stage.close());
        userSession.setLoggedUser(null);
        for (Long id : createdUsers) {
            jdbc.update("delete from users where id = ?", id);
        }
    }

    private User persistedUser(User.Role role) {
        User user = new User();
        user.setName(role.name());
        user.setLogin("shell_" + java.util.UUID.randomUUID());
        user.setPassword("x");
        user.setRole(role);
        user.setActive(true);
        User saved = userRepository.save(user);
        createdUsers.add(saved.getId());
        return saved;
    }

    private Button buttonWithUserData(String page) {
        for (Node node : root.lookupAll(".button")) {
            if (node instanceof Button button && page.equals(button.getUserData())) {
                return button;
            }
        }
        throw new IllegalStateException("Botão não encontrado: " + page);
    }

    private Button backButton() {
        return (Button) root.lookup("#backButton");
    }

    @Test
    void everyNavigationButtonOpensItsPage_RF69() throws Exception {
        List<String> pages = new ArrayList<>();
        FxTestSupport.runOnFx(() -> {
            for (Node node : root.lookupAll(".button")) {
                if (node instanceof Button button && button.getUserData() instanceof String page) {
                    pages.add(page);
                }
            }
        });
        assertTrue(pages.size() >= 13, "botões de navegação: " + pages);
        for (String page : pages) {
            FxTestSupport.runOnFx(() -> buttonWithUserData(page).fire());
            assertEquals(Page.valueOf(page), navigator.getCurrentPage(), page);
        }
    }

    @Test
    void backIsDisabledAtFirstThenReturnsToThePreviousPage_RN30() throws Exception {
        FxTestSupport.runOnFx(() -> {
            assertTrue(backButton().isDisabled());
            buttonWithUserData("ESTOQUE").fire();
            assertTrue(backButton().isDisabled());
            buttonWithUserData("CLIENTES").fire();
            assertFalse(backButton().isDisabled());
            backButton().fire();
            assertEquals(Page.ESTOQUE, navigator.getCurrentPage());
        });
    }

    @Test
    void functionKeysStillOpenTheirPages_RF69() throws Exception {
        FxTestSupport.runOnFx(() -> {
            scene.getAccelerators().get(new KeyCodeCombination(KeyCode.F3)).run();
            assertEquals(Page.ESTOQUE, navigator.getCurrentPage());
            scene.getAccelerators().get(new KeyCodeCombination(KeyCode.F4)).run();
            assertEquals(Page.CAIXA, navigator.getCurrentPage());
            scene.getAccelerators().get(new KeyCodeCombination(KeyCode.F6)).run();
            assertEquals(Page.CLIENTES, navigator.getCurrentPage());
        });
    }

    @Test
    void managerSeesTheConfigButton_RF69() throws Exception {
        FxTestSupport.runOnFx(() -> assertTrue(buttonWithUserData("CONFIG").isVisible()));
    }

    @Test
    void operatorDoesNotSeeTheConfigButton_RF69() throws Exception {
        FxTestSupport.runOnFx(() -> stage.close());
        userSession.setLoggedUser(persistedUser(User.Role.OPERADOR));
        openAgain();
        FxTestSupport.runOnFx(() -> assertFalse(buttonWithUserData("CONFIG").isVisible()));
    }

    private void openAgain() throws Exception {
        FxTestSupport.runOnFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_layout.fxml"));
                loader.setControllerFactory(context::getBean);
                root = loader.load();
                scene = new Scene(root, 1365, 720);
                stage = new Stage();
                stage.setScene(scene);
                stage.show();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
    }
}
