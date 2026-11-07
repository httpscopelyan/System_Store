package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.controller.ConfigPrinterController;
import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrinterTestPageTests {

    private final List<String> printed = new ArrayList<>();
    private final PrinterPort recordingPrinter = new PrinterPort() {
        @Override public void print(String content) { printed.add(content); }
        @Override public void openDrawer() { }
    };
    private final AppSettingService settingsStub = new AppSettingService(null) {
        @Override public String getString(String key, String defaultValue) {
            return defaultValue;
        }
    };

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void toolkit() {
        FxTestSupport.startToolkit();
    }

    @Test
    void testPageHasTitleRulerAndIsNotFiscal() {
        String page = new ReceiptService(settingsStub, recordingPrinter).buildPrinterTest();

        assertTrue(page.contains("TESTE DE IMPRESSAO"));
        assertTrue(page.contains("1234567890123456789012345678901234567890"));
        assertTrue(page.contains("NAO E DOCUMENTO FISCAL"));
        page.lines().forEach(line -> assertTrue(line.length() <= 40, "linha maior que a bobina: " + line));
    }

    @Test
    void printingTheTestSendsExactlyOneJobToThePrinter() {
        new ReceiptService(settingsStub, recordingPrinter).printPrinterTest();

        assertEquals(1, printed.size());
        assertTrue(printed.get(0).contains("TESTE DE IMPRESSAO"));
    }

    @Test
    @DisabledIf("isHeadless")
    void settingsButtonPrintsAndReportsFailureWhenThePrinterIsOff() throws Exception {
        PaymentScreenFixture screen = new PaymentScreenFixture();
        PrinterPort offline = new PrinterPort() {
            @Override public void print(String content) { throw new RuntimeException("Impressora não encontrada"); }
            @Override public void openDrawer() { }
        };
        screen.load("config_printer.fxml", fixture ->
                new ConfigPrinterController(new ReceiptService(settingsStub, offline)));

        screen.onFx(() -> ((Button) screen.node("testButton")).fire());

        assertTrue(screen.statusContainsIn("messageLabel", "Impressora não encontrada", 5));
        assertTrue(!screen.node("testButton").isDisabled());
    }

    @Test
    @DisabledIf("isHeadless")
    void settingsButtonConfirmsWhenThePageWasSent() throws Exception {
        PaymentScreenFixture screen = new PaymentScreenFixture();
        screen.load("config_printer.fxml", fixture ->
                new ConfigPrinterController(new ReceiptService(settingsStub, recordingPrinter)));

        screen.onFx(() -> ((Button) screen.node("testButton")).fire());

        assertTrue(screen.statusContainsIn("messageLabel", "Página de teste enviada", 5));
        assertEquals(1, printed.size());
    }
}
