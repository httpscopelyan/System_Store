package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.NotificationException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.notification.AlertNotifier;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.ProductService;
import br.com.scopel.pfv.beta.service.alert.StockAlertFormatter;
import br.com.scopel.pfv.beta.service.alert.StockAlertMailService;
import br.com.scopel.pfv.beta.service.alert.StockAlertScheduler;
import br.com.scopel.pfv.beta.service.alert.StockAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class StockAlertMailServiceTests {

    private static final class RecordingNotifier implements AlertNotifier {
        final List<String> bodies = new ArrayList<>();
        final List<List<String>> recipientLists = new ArrayList<>();
        boolean deliverable = true;
        boolean failing = false;

        @Override
        public void send(String subject, String body, List<String> recipients) {
            if (failing) {
                throw new NotificationException("servidor fora do ar");
            }
            bodies.add(body);
            recipientLists.add(recipients);
        }

        @Override
        public boolean canDeliver() {
            return deliverable;
        }
    }

    @Autowired
    private StockAlertService stockAlertService;

    @Autowired
    private StockAlertFormatter formatter;

    @Autowired
    private AppSettingService appSettingService;

    @Autowired
    private ProductService productService;

    private RecordingNotifier notifier;
    private StockAlertMailService mailService;
    private String lowStockDescription;

    @BeforeEach
    void setUp() {
        notifier = new RecordingNotifier();
        mailService = new StockAlertMailService(stockAlertService, formatter, notifier, appSettingService);
        String code = "mail " + UUID.randomUUID().toString().substring(0, 8);
        lowStockDescription = "Item " + code;
        productService.create(code, lowStockDescription, Product.Unidade.UN, new BigDecimal("5.00"),
                new BigDecimal("3.00"), new BigDecimal("1"), new BigDecimal("10"), null, true);
    }

    private void enableEmail(String recipients) {
        appSettingService.set(AppSettingService.ALERTS_EMAIL_ENABLED, "true");
        appSettingService.set(AppSettingService.ALERTS_EMAIL_TO, recipients);
    }

    @Test
    void nothingIsSentWhileEmailAlertsAreDisabled_RF55() {
        appSettingService.set(AppSettingService.ALERTS_EMAIL_ENABLED, "false");
        appSettingService.set(AppSettingService.ALERTS_EMAIL_TO, "dono@mercado.com");
        assertFalse(mailService.sendScheduledIfDue(LocalDateTime.now()));
        assertTrue(notifier.bodies.isEmpty());
    }

    @Test
    void scheduledSendDeliversTheListToEveryRecipient_RF55() {
        enableEmail("dono@mercado.com, gerente@mercado.com");
        assertTrue(mailService.sendScheduledIfDue(LocalDateTime.now()));
        assertEquals(1, notifier.bodies.size());
        assertTrue(notifier.bodies.get(0).contains(lowStockDescription));
        assertEquals(List.of("dono@mercado.com", "gerente@mercado.com"), notifier.recipientLists.get(0));
    }

    @Test
    void identicalListInsideTheIntervalIsNotSentAgain_RN23() {
        enableEmail("dono@mercado.com");
        LocalDateTime now = LocalDateTime.now();
        assertTrue(mailService.sendScheduledIfDue(now));
        assertFalse(mailService.sendScheduledIfDue(now.plusMinutes(30)));
        assertEquals(1, notifier.bodies.size());
    }

    @Test
    void identicalListIsSentAgainAfterTheInterval_RN23() {
        enableEmail("dono@mercado.com");
        appSettingService.set(AppSettingService.ALERTS_EMAIL_INTERVAL_MINUTES, "60");
        LocalDateTime now = LocalDateTime.now();
        assertTrue(mailService.sendScheduledIfDue(now));
        assertTrue(mailService.sendScheduledIfDue(now.plusMinutes(61)));
        assertEquals(2, notifier.bodies.size());
    }

    @Test
    void changedListIsSentEvenInsideTheInterval_RN23() {
        enableEmail("dono@mercado.com");
        LocalDateTime now = LocalDateTime.now();
        assertTrue(mailService.sendScheduledIfDue(now));
        productService.create("mail " + UUID.randomUUID().toString().substring(0, 8), "Outro item",
                Product.Unidade.UN, new BigDecimal("5.00"), new BigDecimal("3.00"), new BigDecimal("0"),
                new BigDecimal("10"), null, true);
        assertTrue(mailService.sendScheduledIfDue(now.plusMinutes(5)));
    }

    @Test
    void intervalNeverGoesBelowFifteenMinutes_RN23() {
        appSettingService.set(AppSettingService.ALERTS_EMAIL_INTERVAL_MINUTES, "1");
        assertEquals(15, mailService.intervalMinutes());
    }

    @Test
    void scheduledSendSkipsWhenTheNotifierCannotDeliver_RN23() {
        enableEmail("dono@mercado.com");
        notifier.deliverable = false;
        assertFalse(mailService.sendScheduledIfDue(LocalDateTime.now()));
    }

    @Test
    void manualSendWithoutRecipientsIsRefusedWithAClearMessage_RF55() {
        appSettingService.set(AppSettingService.ALERTS_EMAIL_TO, "");
        assertThrows(NotificationException.class, () -> mailService.sendNow());
        assertFalse(mailService.hasRecipients());
    }

    @Test
    void manualSendWithoutSmtpIsRefusedWithAClearMessage_RF55() {
        appSettingService.set(AppSettingService.ALERTS_EMAIL_TO, "dono@mercado.com");
        notifier.deliverable = false;
        NotificationException refusal = assertThrows(NotificationException.class, () -> mailService.sendNow());
        assertTrue(refusal.getMessage().contains("SMTP"));
    }

    @Test
    void manualSendReturnsTheNumberOfListedItems_RF55() {
        appSettingService.set(AppSettingService.ALERTS_EMAIL_TO, "dono@mercado.com");
        assertEquals(stockAlertService.lowStockItems().size(), mailService.sendNow());
        assertEquals(1, notifier.bodies.size());
    }

    @Test
    void schedulerSwallowsNotifierFailuresAndKeepsRunning_RN23() {
        enableEmail("dono@mercado.com");
        notifier.failing = true;
        StockAlertScheduler scheduler = new StockAlertScheduler(mailService);
        assertDoesNotThrow(scheduler::runOnce);
    }
}
