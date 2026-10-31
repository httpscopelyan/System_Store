package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.ThemeManager;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.util.UUID;

@SpringBootTest
@EnabledIfSystemProperty(named = "snapshot.dir", matches = ".+")
class ScreenSnapshotTool {

    @Autowired private ApplicationContext context;
    @Autowired private UserSession userSession;
    @Autowired private UserRepository userRepository;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private ThemeManager themeManager;

    @BeforeAll
    static void startToolkit() {
        FxTestSupport.startToolkit();
    }

    @Test
    void snapshotPages() throws Exception {
        User operator = new User();
        operator.setName("Operador");
        operator.setLogin("snap_" + UUID.randomUUID());
        operator.setPassword("x");
        operator.setRole(User.Role.GERENTE);
        operator.setActive(true);
        operator = userRepository.save(operator);
        CashRegister cashRegister = cashRegisterService.open(operator, new BigDecimal("50.00"));
        userSession.setLoggedUser(operator);
        userSession.setCaixaAtual(cashRegister);
        userSession.setVendaAtual(null);

        String directory = System.getProperty("snapshot.dir");
        for (String page : System.getProperty("snapshot.pages", "sale").split(",")) {
            FxTestSupport.runOnFx(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + page + ".fxml"));
                    loader.setControllerFactory(context::getBean);
                    Parent root = loader.load();
                    themeManager.apply(root);
                    Scene scene = new Scene(root, 1365, 720);
                    Stage stage = new Stage();
                    stage.setScene(scene);
                    stage.show();
                    root.applyCss();
                    root.layout();
                    WritableImage image = scene.snapshot(null);
                    save(image, new File(directory, page + ".png"));
                    stage.close();
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
        }
        userSession.setCaixaAtual(null);
        cashRegisterService.close(cashRegister.getId(), new BigDecimal("50.00"));
    }

    private void save(WritableImage image, File file) throws Exception {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ImageIO.write(buffered, "png", file);
    }
}
