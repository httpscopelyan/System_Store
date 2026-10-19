package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.ThemeManager;
import br.com.scopel.pfv.beta.service.AppSettingService;
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

import java.awt.*;

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
		stage.setScene(scene);
		stage.setTitle("SCOPEL STORE");

		// Tela cheia sem bordas, se configurado.
		if (springContext.getBean(AppSettingService.class).getBoolean("ui.fullscreen", false)) {
			stage.setFullScreenExitHint("Pressione ESC para sair da tela cheia");
			stage.setFullScreen(true);
		}

		stage.show();
	}

	@Override
	public void stop() {
		springContext.close();
	}

	public static void main(String[] args) {
		launch(args);
	}

}
