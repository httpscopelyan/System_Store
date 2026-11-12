package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.PaymentScreenFixture.ScriptedGateway;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.controller.PagamentoDinheiroController;
import br.com.scopel.pfv.beta.controller.PagamentoPixController;
import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.service.PixService;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.awt.GraphicsEnvironment;
import java.math.BigDecimal;

import static br.com.scopel.pfv.beta.hardware.CardPaymentStatus.APPROVED;
import static br.com.scopel.pfv.beta.hardware.CardPaymentStatus.DECLINED;
import static br.com.scopel.pfv.beta.hardware.CardPaymentStatus.PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisabledIf("isHeadless")
class PixAndCashScreenTests {

    private static final int POLL_WAIT = 12;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void toolkit() {
        FxTestSupport.startToolkit();
    }

    private PaymentScreenFixture openPix(ScriptedGateway gateway) throws Exception {
        PaymentScreenFixture screen = new PaymentScreenFixture();
        PixService pixService = mock(PixService.class);
        when(pixService.buildFromSettings(any(), anyString())).thenReturn("00020126PAYLOAD");
        when(screen.saleService.payPix(any())).thenReturn(screen.sale);
        screen.load("pagamento_pix.fxml", fixture -> new PagamentoPixController(fixture.saleService,
                fixture.session, fixture.navigator, pixService, fixture.receiptService, gateway));
        return screen;
    }

    @Test
    void pixPaidOnTheMachineRegistersTheSaleOnceAndDoesNotPrintAutomatically() throws Exception {
        PaymentScreenFixture screen = openPix(new ScriptedGateway(true).script(PENDING, APPROVED));

        verify(screen.saleService, timeout(POLL_WAIT * 1000)).payPix(screen.sale);
        verify(screen.navigator, timeout(2000)).navigate(Page.VENDA_FINALIZADA);
        verify(screen.receiptService, never()).printSaleReceipt(any(br.com.scopel.pfv.beta.model.Sale.class),
                any(br.com.scopel.pfv.beta.service.ReceiptPayment.class));
        verify(screen.navigator, never()).navigate(Page.PAGAMENTO);
        assertEquals(PaymentMethod.PIX, screen.session.getUltimaForma());
    }

    @Test
    void pixMachineChargeLocksNavigationAndEscapeAsksBeforeCanceling() throws Exception {
        ScriptedGateway gateway = new ScriptedGateway(true).script(PENDING);
        PaymentScreenFixture screen = openPix(gateway);
        verify(screen.navigator).lock(argThat(reason -> reason.contains("PIX")));
        screen.answerCancellation(false);

        screen.press(KeyCode.ESCAPE);

        assertEquals(0, gateway.cancels.get());
        verify(screen.navigator, never()).navigate(Page.PAGAMENTO);
    }

    @Test
    void pixCancelConfirmedGoesBackToSelectionWithoutRegistering() throws Exception {
        ScriptedGateway gateway = new ScriptedGateway(true).script(PENDING);
        PaymentScreenFixture screen = openPix(gateway);
        Thread.sleep(300);
        screen.answerCancellation(true);

        screen.press(KeyCode.ESCAPE);

        verify(screen.navigator, timeout(5000)).navigate(Page.PAGAMENTO);
        verify(screen.saleService, never()).payPix(any());
    }

    @Test
    void pixDeclinedWaitsForTheOperatorInsteadOfJumpingAway() throws Exception {
        PaymentScreenFixture screen = openPix(new ScriptedGateway(true).script(DECLINED, DECLINED));

        assertTrue(screen.statusContains("Nenhum valor foi registrado", POLL_WAIT));
        Thread.sleep(3000);
        verify(screen.navigator, never()).navigate(Page.PAGAMENTO);

        screen.press(KeyCode.ENTER);

        verify(screen.navigator, timeout(2000)).navigate(Page.PAGAMENTO);
        verify(screen.saleService, never()).payPix(any());
    }

    @Test
    void pixCancelAfterTheCustomerAlreadyPaidStillRegistersTheSale() throws Exception {
        PaymentScreenFixture screen = openPix(new ScriptedGateway(true).script(PENDING, APPROVED).cancelFails());
        Thread.sleep(300);
        screen.answerCancellation(true);

        screen.press(KeyCode.ESCAPE);

        verify(screen.saleService, timeout(POLL_WAIT * 1000)).payPix(screen.sale);
        verify(screen.navigator, never()).navigate(Page.PAGAMENTO);
    }

    @Test
    void manualPixShowsTheCodeAndTheOperatorConfirmsReceipt() throws Exception {
        PaymentScreenFixture screen = openPix(new ScriptedGateway(false));
        verify(screen.navigator, never()).lock(anyString());

        screen.onFx(() -> screen.button("confirmButton").fire());

        verify(screen.saleService).payPix(screen.sale);
        verify(screen.navigator).navigate(Page.VENDA_FINALIZADA);
    }

    @Test
    void cashSaleOpensTheDrawerOffTheScreenThreadAndDoesNotPrintAutomatically() throws Exception {
        PaymentScreenFixture screen = new PaymentScreenFixture();
        PrinterPort printer = mock(PrinterPort.class);
        when(screen.saleService.pay(any(), eq(PaymentMethod.DINHEIRO), any())).thenReturn(screen.sale);
        when(screen.saleService.calculateChange(any(), any())).thenReturn(new BigDecimal("4.10"));
        screen.load("pagamento_dinheiro.fxml", fixture -> new PagamentoDinheiroController(fixture.saleService,
                fixture.session, fixture.navigator, fixture.receiptService, printer));

        screen.onFx(() -> ((TextField) screen.node("amountReceivedField")).setText("30,00"));
        screen.onFx(() -> ((TextField) screen.node("amountReceivedField")).fireEvent(
                new javafx.event.ActionEvent()));

        verify(printer, timeout(3000)).openDrawer();
        verify(screen.navigator).navigate(Page.VENDA_FINALIZADA);
        verify(screen.receiptService, never()).printSaleReceipt(any(br.com.scopel.pfv.beta.model.Sale.class),
                any(br.com.scopel.pfv.beta.service.ReceiptPayment.class));
    }

    @Test
    void cashSaleIsKeptEvenWhenTheDrawerFails() throws Exception {
        PaymentScreenFixture screen = new PaymentScreenFixture();
        PrinterPort printer = mock(PrinterPort.class);
        when(screen.saleService.pay(any(), eq(PaymentMethod.DINHEIRO), any())).thenReturn(screen.sale);
        when(screen.saleService.calculateChange(any(), any())).thenReturn(BigDecimal.ZERO);
        org.mockito.Mockito.doThrow(new RuntimeException("sem papel")).when(printer).openDrawer();
        screen.load("pagamento_dinheiro.fxml", fixture -> new PagamentoDinheiroController(fixture.saleService,
                fixture.session, fixture.navigator, fixture.receiptService, printer));

        screen.onFx(() -> ((TextField) screen.node("amountReceivedField")).setText("25,90"));
        screen.onFx(() -> ((TextField) screen.node("amountReceivedField")).fireEvent(
                new javafx.event.ActionEvent()));

        verify(screen.navigator, timeout(3000)).navigate(Page.VENDA_FINALIZADA);
        verify(screen.navigator, timeout(3000)).notice(argThat(message -> message.contains("sem papel")));
    }
}
