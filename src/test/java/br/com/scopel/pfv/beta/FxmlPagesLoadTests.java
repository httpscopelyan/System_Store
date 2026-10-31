package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.Page;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@DisabledIf("isHeadless")
class FxmlPagesLoadTests {

    @Autowired
    private ApplicationContext context;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException toolkitAlreadyStarted) {
            Platform.setImplicitExit(false);
        }
    }

    @ParameterizedTest
    @EnumSource(Page.class)
    void everyPageLoadsWithItsSpringController(Page page) throws Exception {
        CompletableFuture<Object> loaded = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(Page.class.getResource(page.getPath()));
                loader.setControllerFactory(context::getBean);
                loaded.complete(loader.load());
            } catch (Throwable failure) {
                loaded.completeExceptionally(failure);
            }
        });
        assertNotNull(loaded.get(30, TimeUnit.SECONDS));
    }
}
