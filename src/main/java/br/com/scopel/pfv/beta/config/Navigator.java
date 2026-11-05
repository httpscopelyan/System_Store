package br.com.scopel.pfv.beta.config;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class Navigator {

    private static final double MIN_PAGE_HEIGHT = 700;

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

    private ScrollPane scrollable(Parent content) {
        if (content instanceof Region region) {
            region.setMinHeight(Math.max(MIN_PAGE_HEIGHT, region.minHeight(-1)));
        }
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setFocusTraversable(false);
        scroll.getStyleClass().add("page-scroll");
        return scroll;
    }

    private void show(Page page) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(page.getPath()));
            loader.setControllerFactory(context::getBean);
            Parent content = loader.load();
            themeManager.apply(content);
            shell.setCenter(scrollable(content));
            currentPage = page;
            navigationListeners.forEach(Runnable::run);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar a página " + page, e);
        }
    }
}
