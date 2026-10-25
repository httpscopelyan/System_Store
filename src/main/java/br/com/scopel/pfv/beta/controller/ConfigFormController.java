package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.ThemeManager;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.PixService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

@Component
public class ConfigFormController {

    private final AppSettingService settings;
    private final ThemeManager themeManager;

    @FXML private CheckBox repeatItemBox;
    @FXML private CheckBox negativeStockBox;
    @FXML private CheckBox allowCreditBox;
    @FXML private TextField discountLimitField;
    @FXML private TextField storeNameField;
    @FXML private TextField pixKeyField;
    @FXML private TextField pixNameField;
    @FXML private TextField pixCityField;
    @FXML private ComboBox<String> themeBox;
    @FXML private CheckBox fullscreenBox;
    @FXML private Label messageLabel;

    public static final String FULLSCREEN = "ui.fullscreen";

    public ConfigFormController(AppSettingService settings, ThemeManager themeManager) {
        this.settings = settings;
        this.themeManager = themeManager;
    }

    @FXML
    public void initialize() {
        if (themeBox != null) {
            themeBox.setItems(FXCollections.observableArrayList(
                    "classic", "sky", "turquoise", "silver", "dark", "navy"));
            themeBox.setValue(themeManager.current());
        }
        if (fullscreenBox != null) {
            fullscreenBox.setSelected(settings.getBoolean(FULLSCREEN, false));
        }
        repeatItemBox.setSelected(settings.getBoolean(AppSettingService.SALE_REPEAT_ITEM, true));
        negativeStockBox.setSelected(settings.getBoolean(AppSettingService.SALE_ALLOW_NEGATIVE_STOCK, false));
        allowCreditBox.setSelected(settings.getBoolean(AppSettingService.CREDIT_ALLOW_CREDIT_SALE, false));
        discountLimitField.setText(settings.getString(AppSettingService.SALE_DISCOUNT_LIMIT_PERCENT, "10"));
        storeNameField.setText(settings.getString("store.name", "MERCADO"));
        pixKeyField.setText(settings.getString(PixService.PIX_KEY, ""));
        pixNameField.setText(settings.getString(PixService.PIX_MERCHANT_NAME, ""));
        pixCityField.setText(settings.getString(PixService.PIX_MERCHANT_CITY, ""));
    }

    @FXML
    public void onSave() {
        try {
            settings.set(AppSettingService.SALE_REPEAT_ITEM, String.valueOf(repeatItemBox.isSelected()));
            settings.set(AppSettingService.SALE_ALLOW_NEGATIVE_STOCK, String.valueOf(negativeStockBox.isSelected()));
            settings.set(AppSettingService.CREDIT_ALLOW_CREDIT_SALE, String.valueOf(allowCreditBox.isSelected()));
            settings.set(AppSettingService.SALE_DISCOUNT_LIMIT_PERCENT, discountLimitField.getText().trim());
            settings.set("store.name", storeNameField.getText().trim());
            settings.set(PixService.PIX_KEY, pixKeyField.getText().trim());
            settings.set(PixService.PIX_MERCHANT_NAME, pixNameField.getText().trim());
            settings.set(PixService.PIX_MERCHANT_CITY, pixCityField.getText().trim());

            if (themeBox != null && themeBox.getValue() != null) {
                themeManager.setCurrent(themeBox.getValue());
                // Aplica na janela atual (efeito completo ao navegar/reabrir).
                if (messageLabel.getScene() != null) {
                    themeManager.apply(messageLabel.getScene().getRoot());
                }
            }
            if (fullscreenBox != null) {
                settings.set(FULLSCREEN, String.valueOf(fullscreenBox.isSelected()));
                if (messageLabel.getScene() != null
                        && messageLabel.getScene().getWindow() instanceof Stage stage) {
                    stage.setFullScreenExitHint("Pressione ESC para sair da tela cheia");
                    stage.setFullScreen(fullscreenBox.isSelected());
                }
            }
            messageLabel.setText("Configurações salvas! (tema aplica ao navegar)");
        } catch (Exception e) {
            messageLabel.setText("Não foi possível salvar");
        }
    }
}
