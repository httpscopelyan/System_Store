package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.service.AppSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AppSettingServiceTests {

    @Autowired
    private AppSettingService settings;

    // Valores semeados na migration V11.
    @Test
    void readsSeededDefaults() {
        assertTrue(settings.getBoolean(AppSettingService.SALE_REPEAT_ITEM, false));
        assertFalse(settings.getBoolean(AppSettingService.SALE_ALLOW_NEGATIVE_STOCK, true));
        assertEquals(10, settings.getInt(AppSettingService.SALE_DISCOUNT_LIMIT_PERCENT, 0));
    }

    // Chave inexistente cai no default (config faltando não derruba operação).
    @Test
    void missingKeyFallsBackToDefault() {
        assertEquals("x", settings.getString("nao.existe." + System.nanoTime(), "x"));
        assertEquals(42, settings.getInt("nao.existe." + System.nanoTime(), 42));
    }

    // set cria e depois atualiza.
    @Test
    void setCreatesThenUpdates() {
        String key = "test.key." + System.nanoTime();

        settings.set(key, "false");
        assertFalse(settings.getBoolean(key, true));

        settings.set(key, "true");
        assertTrue(settings.getBoolean(key, false));
    }
}
