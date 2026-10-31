package br.com.scopel.pfv.beta.config;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class Navigator {

    private final ApplicationContext context;
    private final ThemeManager themeManager;
    private final NavigationHistory history = new NavigationHistory();
    private final List<Runnable> navigationListeners = new ArrayList<>();

    private BorderPane shell;
    private Page currentPage;

    public Navigator(ApplicationContext context, ThemeManager themeManager) {
        this.context = context;
        this.themeManager = themeManager;
    }

    public void bind(BorderPane shell) {
        this.shell = shell;
        navigationListeners.clear();
        history.clear();
        currentPage = null;
    }

    public void navigate(Page page) {
        if (currentPage != null && currentPage != page) {
            history.push(currentPage);
        }
        show(page);
    }

    public void back() {
        Page target = history.back().orElse(Page.INICIO);
        show(target);
    }

    public boolean canGoBack() {
        return history.canGoBack();
    }

    public Page getCurrentPage() {
        return currentPage;
    }

    public void addNavigationListener(Runnable listener) {
        navigationListeners.add(listener);
    }

    private void show(Page page) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(page.getPath()));
            loader.setControllerFactory(context::getBean);
            Parent content = loader.load();
            themeManager.apply(content);
            shell.setCenter(content);
            currentPage = page;
            navigationListeners.forEach(Runnable::run);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar a página " + page, e);
        }
    }
}
