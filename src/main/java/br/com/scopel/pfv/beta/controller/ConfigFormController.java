package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.ThemeManager;
import br.com.scopel.pfv.beta.controller.support.DecimalInput;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.PixService;
import br.com.scopel.pfv.beta.service.StockCostMode;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ConfigFormController {

    private final AppSettingService settings;
    private final ThemeManager themeManager;

    @FXML private CheckBox repeatItemBox;
    @FXML private CheckBox negativeStockBox;
    @FXML private CheckBox allowCreditBox;
    @FXML private CheckBox askQuantityBox;
    @FXML private ComboBox<StockCostMode> costModeBox;
    @FXML private TextField maxQuantityField;
    @FXML private TextField discountLimitField;
    @FXML private TextField storeNameField;
    @FXML private TextField defaultMarginField;
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
        costModeBox.setItems(FXCollections.observableArrayList(StockCostMode.values()));
        costModeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(StockCostMode mode) {
                return mode == null ? "" : mode.label();
            }

            @Override
            public StockCostMode fromString(String text) {
                return null;
            }
        });
        costModeBox.setValue(StockCostMode.fromSetting(settings.getString(AppSettingService.STOCK_COST_MODE, null)));
        askQuantityBox.setSelected(settings.getBoolean(AppSettingService.SALE_ASK_QUANTITY_AFTER_SCAN, true));
        maxQuantityField.setText(settings.getString(AppSettingService.SALE_MAX_QUANTITY_PER_ITEM, "999"));
        discountLimitField.setText(settings.getString(AppSettingService.SALE_DISCOUNT_LIMIT_PERCENT, "10"));
        storeNameField.setText(settings.getString(AppSettingService.STORE_NAME, "MERCADO"));
        defaultMarginField.setText(settings.getString(AppSettingService.PRICING_DEFAULT_MARGIN_PERCENT, "30"));
        pixKeyField.setText(settings.getString(PixService.PIX_KEY, ""));
        pixNameField.setText(settings.getString(PixService.PIX_MERCHANT_NAME, ""));
        pixCityField.setText(settings.getString(PixService.PIX_MERCHANT_CITY, ""));
    }

    @FXML
    public void onSave() {
        BigDecimal defaultMargin = DecimalInput.parseOrNull(defaultMarginField.getText());
        if (defaultMargin == null || defaultMargin.signum() < 0) {
            messageLabel.setText("Margem padrão inválida: informe um número maior ou igual a zero.");
            return;
        }
        BigDecimal maxQuantity = DecimalInput.parseOrNull(maxQuantityField.getText());
        if (maxQuantity == null || maxQuantity.signum() <= 0) {
            messageLabel.setText("Quantidade máxima inválida: informe um número maior que zero.");
            return;
        }
        try {
            settings.set(AppSettingService.SALE_REPEAT_ITEM, String.valueOf(repeatItemBox.isSelected()));
            settings.set(AppSettingService.SALE_ALLOW_NEGATIVE_STOCK, String.valueOf(negativeStockBox.isSelected()));
            settings.set(AppSettingService.STOCK_COST_MODE, (costModeBox.getValue() == null
                    ? StockCostMode.ULTIMO : costModeBox.getValue()).name());
            settings.set(AppSettingService.SALE_ASK_QUANTITY_AFTER_SCAN, String.valueOf(askQuantityBox.isSelected()));
            settings.set(AppSettingService.SALE_MAX_QUANTITY_PER_ITEM,
                    maxQuantity.stripTrailingZeros().toPlainString());
            settings.set(AppSettingService.CREDIT_ALLOW_CREDIT_SALE, String.valueOf(allowCreditBox.isSelected()));
            settings.set(AppSettingService.SALE_DISCOUNT_LIMIT_PERCENT, discountLimitField.getText().trim());
            settings.set(AppSettingService.STORE_NAME, storeNameField.getText().trim());
            settings.set(AppSettingService.PRICING_DEFAULT_MARGIN_PERCENT,
                    defaultMargin.stripTrailingZeros().toPlainString());
            settings.set(PixService.PIX_KEY, pixKeyField.getText().trim());
            settings.set(PixService.PIX_MERCHANT_NAME, pixNameField.getText().trim());
            settings.set(PixService.PIX_MERCHANT_CITY, pixCityField.getText().trim());

            if (themeBox != null && themeBox.getValue() != null) {
                themeManager.setCurrent(themeBox.getValue());
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
