package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.SingleInstanceGuard;
import br.com.scopel.pfv.beta.config.ThemeManager;
import br.com.scopel.pfv.beta.controller.support.ScannerGate;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.UpdateService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.Label;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class BetaApplication extends Application{

	public static ConfigurableApplicationContext springContext;

	@Override
	public void init() {
		springContext = new SpringApplicationBuilder(BetaApplication.class).run();
	}

	@Override
	public void start(Stage stage) throws Exception {
		FXMLLoader loader = new FXMLLoader(
				getClass().getResource("/fxml/home.fxml")
		);
		loader.setControllerFactory(springContext::getBean);
		Parent homeRoot = loader.load();
		springContext.getBean(ThemeManager.class).apply(homeRoot);
		Scene scene = new Scene(homeRoot, 1365, 720);
		Navigator navigator = springContext.getBean(Navigator.class);
		new ScannerGate(navigator::getCurrentPage, navigator::notice).install(scene);
		stage.setScene(scene);
		stage.setTitle("SCOPEL STORE");

		if (springContext.getBean(AppSettingService.class).getBoolean("ui.fullscreen", false)) {
			stage.setFullScreenExitHint("Pressione ESC para sair da tela cheia");
			stage.setFullScreen(true);
		}

		stage.show();

		Thread startedMarker = new Thread(() -> springContext.getBean(UpdateService.class).markStarted(),
				"update-started-marker");
		startedMarker.setDaemon(true);
		startedMarker.start();
	}

	@Override
	public void stop() {
		springContext.close();
	}

	public static void main(String[] args) {
		if (!SingleInstanceGuard.acquire()) {
			return;
		}
		launch(args);
	}

}
