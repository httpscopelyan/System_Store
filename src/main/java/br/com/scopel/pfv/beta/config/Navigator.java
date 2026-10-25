package br.com.scopel.pfv.beta.config;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Roteador de páginas: troca somente o centro do shell (janela única).
 * Usa o ApplicationContext injetado como controllerFactory, para que cada
 * controller de página seja um bean Spring (nunca {@code new}).
 */
@Component
public class Navigator {

    private final ApplicationContext context;
    private final ThemeManager themeManager;

    private BorderPane shell;

    // Histórico mínimo (1 nível): a página mostrada antes da atual. Permite que
    // telas auxiliares (ex.: Consulta de Preço) voltem para a tela anterior sem
    // conhecê-la. Não é uma pilha completa — basta o "voltar" de um passo.
    private Page currentPage;
    private Page previousPage;

    public Navigator(ApplicationContext context, ThemeManager themeManager) {
        this.context = context;
        this.themeManager = themeManager;
    }

    /** O MainController chama isto no initialize(), passando o BorderPane raiz. */
    public void bind(BorderPane shell) {
        this.shell = shell;
    }

    /** Carrega a página e a coloca no centro do shell, preservando a janela. */
    public void navigate(Page page) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(page.getPath()));
            loader.setControllerFactory(context::getBean);
            Parent content = loader.load();
            themeManager.apply(content);
            shell.setCenter(content);
            previousPage = currentPage;
            currentPage = page;
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar a página " + page, e);
        }
    }

    /** Página mostrada antes da atual (pode ser null logo após o login). */
    public Page getPreviousPage() {
        return previousPage;
    }
}
