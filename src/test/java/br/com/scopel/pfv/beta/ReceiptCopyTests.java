package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.controller.VendaFinalizadaController;
import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.awt.GraphicsEnvironment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReceiptCopyTests {

    private final SplitPaymentService splitPaymentService = org.mockito.Mockito.mock(SplitPaymentService.class);
    private final List<String> printed = new CopyOnWriteArrayList<>();
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

    private Sale finishedSale() {
        Product product = new Product();
        product.setDescription("Arroz 5kg");
        SaleItem item = new SaleItem();
        item.setProduct(product);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("25.90"));
        item.setTotalPrice(new BigDecimal("25.90"));
        Sale sale = new Sale();
        sale.setCreatedAt(LocalDateTime.now());
        sale.setDiscount(BigDecimal.ZERO);
        sale.setSurcharge(BigDecimal.ZERO);
        sale.setTotal(new BigDecimal("25.90"));
        sale.getItems().add(item);
        return sale;
    }

    private PaymentScreenFixture openFinishedScreen(PaymentMethod method, CardType cardType, BigDecimal change)
            throws Exception {
        PaymentScreenFixture screen = new PaymentScreenFixture();
        screen.session.setUltimaVenda(finishedSale());
        screen.session.setUltimaForma(method);
        screen.session.setCardTypeSelecionado(cardType);
        screen.session.setUltimoTroco(change);
        screen.load("venda_finalizada.fxml", fixture -> new VendaFinalizadaController(fixture.session,
                fixture.navigator, new ReceiptService(settingsStub, recordingPrinter), splitPaymentService));
        return screen;
    }

    @Test
    void directChargeCopyShowsAmountMethodNoteAndSecondVia() {
        ReceiptService service = new ReceiptService(settingsStub, recordingPrinter);
        br.com.scopel.pfv.beta.model.DirectCharge charge = new br.com.scopel.pfv.beta.model.DirectCharge();
        charge.setAmount(new BigDecimal("12.50"));
        charge.setMethod(PaymentMethod.CARTAO);
        charge.setCardType(CardType.DEBITO);
        charge.setNote("Fiado do João");
        charge.setCreatedAt(LocalDateTime.now());

        String copy = service.buildDirectChargeReceiptCopy(charge);

        assertTrue(copy.contains("2a VIA") && copy.contains("12,50") && copy.contains("Debito"));
        assertTrue(copy.contains("Fiado do João"));
    }

    @Test
    void copyIsMarkedAsSecondViaAndKeepsItemsAndPayment() {
        ReceiptService service = new ReceiptService(settingsStub, recordingPrinter);

        String copy = service.buildSaleReceiptCopy(finishedSale(),
                br.com.scopel.pfv.beta.service.ReceiptPayment.pix());

        assertTrue(copy.contains("2a VIA"));
        assertTrue(copy.contains("Arroz 5kg"));
        assertTrue(copy.contains("PIX"));
        assertFalse(service.buildSaleReceipt(finishedSale()).contains("2a VIA"), "a via original não é marcada");
    }

    @Test
    @DisabledIf("isHeadless")
    void finishedSaleScreenHasTheReissueButtonNextToNewSaleAndHome() throws Exception {
        PaymentScreenFixture screen = openFinishedScreen(PaymentMethod.PIX, null, null);

        Button reissue = screen.button("novaViaButton");

        assertEquals("Emitir nova via", reissue.getText());
        assertEquals(reissue.getParent(), screen.button("novaVendaButton").getParent());
    }

    @Test
    @DisabledIf("isHeadless")
    void reissuingACashSalePrintsTheSecondViaWithReceivedAndChange() throws Exception {
        PaymentScreenFixture screen = openFinishedScreen(PaymentMethod.DINHEIRO, null, new BigDecimal("4.10"));

        screen.onFx(() -> screen.button("novaViaButton").fire());

        assertTrue(screen.waitUntil(() -> printed.size() == 1, 5));
        String copy = printed.get(0);
        assertTrue(copy.contains("2a VIA") && copy.contains("Dinheiro"));
        assertTrue(copy.contains("30,00") && copy.contains("4,10"), "recebido = total + troco");
        verify(screen.navigator, never()).navigate(Page.VENDA);
    }

    @Test
    @DisabledIf("isHeadless")
    void reissuingACardSaleNamesTheCardType() throws Exception {
        PaymentScreenFixture screen = openFinishedScreen(PaymentMethod.CARTAO, CardType.CREDITO, null);

        screen.onFx(() -> screen.button("novaViaButton").fire());

        assertTrue(screen.waitUntil(() -> printed.size() == 1, 5));
        assertTrue(printed.get(0).contains("Cartao de Credito"));
    }

    @Test
    @DisabledIf("isHeadless")
    void reissuingTwiceGeneratesTwoCopiesAndFailureIsShownToTheOperator() throws Exception {
        PaymentScreenFixture screen = openFinishedScreen(PaymentMethod.PIX, null, null);
        screen.onFx(() -> screen.button("novaViaButton").fire());
        screen.onFx(() -> screen.button("novaViaButton").fire());
        assertTrue(screen.waitUntil(() -> printed.size() == 2, 5));

        PaymentScreenFixture offline = new PaymentScreenFixture();
        offline.session.setUltimaVenda(finishedSale());
        offline.session.setUltimaForma(PaymentMethod.PIX);
        PrinterPort broken = new PrinterPort() {
            @Override public void print(String content) { throw new RuntimeException("sem papel"); }
            @Override public void openDrawer() { }
        };
        offline.load("venda_finalizada.fxml", fixture -> new VendaFinalizadaController(fixture.session,
                fixture.navigator, new ReceiptService(settingsStub, broken), splitPaymentService));
        offline.onFx(() -> offline.button("novaViaButton").fire());

        assertTrue(offline.statusContainsIn("copyStatusLabel", "sem papel", 5));
    }

    @Test
    @DisabledIf("isHeadless")
    void enterStillStartsANewSaleAndNothingIsPrintedWithoutAClick() throws Exception {
        PaymentScreenFixture screen = openFinishedScreen(PaymentMethod.PIX, null, null);

        screen.press(javafx.scene.input.KeyCode.ENTER);

        verify(screen.navigator).navigate(Page.VENDA);
        assertEquals(0, printed.size());
    }
}
