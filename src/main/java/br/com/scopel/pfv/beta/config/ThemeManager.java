package br.com.scopel.pfv.beta.config;

import br.com.scopel.pfv.beta.service.AppSettingService;
import javafx.scene.Parent;
import org.springframework.stereotype.Component;

/**
 * Seletor de temas (replica o "Tema:" do PDV Prático, que usa VCL Styles).
 * Aplica um CSS de tema POR CIMA do styles.css em cada raiz carregada, então o
 * tema vence no cascade sem mexer no CSS base. O tema fica salvo em app_setting.
 */
@Component
public class ThemeManager {

    public static final String KEY = "ui.theme";
    public static final String DEFAULT = "classic";

    private final AppSettingService settings;

    public ThemeManager(AppSettingService settings) {
        this.settings = settings;
    }

    public String current() {
        String t = settings.getString(KEY, DEFAULT);
        return (t == null || t.isBlank()) ? DEFAULT : t;
    }

    public void setCurrent(String theme) {
        settings.set(KEY, theme);
    }

    /** Garante styles.css na raiz e aplica o tema atual por cima (override). */
    public void apply(Parent root) {
        if (root == null) {
            return;
        }
        String base = getClass().getResource("/css/styles.css").toExternalForm();
        if (!root.getStylesheets().contains(base)) {
            root.getStylesheets().add(base);
        }
        // remove tema anterior
        root.getStylesheets().removeIf(s -> s.contains("/css/theme-"));
        // "navy" = sem override (usa o styles.css original)
        String theme = current();
        if (!"navy".equals(theme)) {
            var res = getClass().getResource("/css/theme-" + theme + ".css");
            if (res != null) {
                root.getStylesheets().add(res.toExternalForm());
            }
        }
    }
}
