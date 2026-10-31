package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.AppSetting;
import br.com.scopel.pfv.beta.repository.AppSettingRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AppSettingService {

    public static final String SALE_REPEAT_ITEM = "sale.repeat_item";
    public static final String SALE_ALLOW_NEGATIVE_STOCK = "sale.allow_negative_stock";
    public static final String SALE_SHOW_STOCK_QTY = "sale.show_stock_qty";
    public static final String SALE_DISCOUNT_LIMIT_PERCENT = "sale.discount_limit_percent";
    public static final String STOCK_CONTROL_VALIDITY = "stock.control_validity";
    public static final String CREDIT_ALLOW_CREDIT_SALE = "credit.allow_credit_sale";
    public static final String CREDIT_BLOCK_OVERDUE = "credit.block_overdue";
    public static final String PRICING_DEFAULT_MARGIN_PERCENT = "pricing.default_margin_percent";
    public static final String STORE_NAME = "store.name";
    public static final String STOCK_COST_MODE = "stock.cost_mode";
    public static final String SALE_ASK_QUANTITY_AFTER_SCAN = "sale.ask_quantity_after_scan";
    public static final String SALE_MAX_QUANTITY_PER_ITEM = "sale.max_quantity_per_item";
    public static final String STOCK_MIN_ALERT_MARGIN = "stock.min_alert_margin";
    public static final String ALERTS_EMAIL_ENABLED = "alerts.email_enabled";
    public static final String ALERTS_EMAIL_TO = "alerts.email_to";
    public static final String ALERTS_EMAIL_INTERVAL_MINUTES = "alerts.email_interval_minutes";
    public static final String ALERTS_SMTP_HOST = "alerts.smtp_host";
    public static final String ALERTS_SMTP_PORT = "alerts.smtp_port";
    public static final String ALERTS_SMTP_USER = "alerts.smtp_user";
    public static final String ALERTS_SMTP_FROM = "alerts.smtp_from";
    public static final String ALERTS_LAST_SENT_DIGEST = "alerts.last_sent_digest";
    public static final String ALERTS_LAST_SENT_AT = "alerts.last_sent_at";

    private final AppSettingRepository repository;

    public AppSettingService(AppSettingRepository repository) {
        this.repository = repository;
    }

    public String getString(String key, String defaultValue) {
        return repository.findByKey(key)
                .map(AppSetting::getValue)
                .orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = getString(key, null);
        return v == null ? defaultValue : Boolean.parseBoolean(v.trim());
    }

    public int getInt(String key, int defaultValue) {
        String v = getString(key, null);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
        String v = getString(key, null);
        if (v == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public AppSetting set(String key, String value) {
        AppSetting setting = repository.findByKey(key).orElseGet(() -> {
            AppSetting s = new AppSetting();
            s.setKey(key);
            return s;
        });
        setting.setValue(value);
        return repository.save(setting);
    }
}
