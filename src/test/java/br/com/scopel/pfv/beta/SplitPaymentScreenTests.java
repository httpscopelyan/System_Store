package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.PaymentScreenFixture.ScriptedGateway;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.controller.PagamentoDivididoController;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentInput;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.service.PixService;
import br.com.scopel.pfv.beta.service.SplitPaymentResult;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
import br.com.scopel.pfv.beta.service.SplitPaymentStatus;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.awt.GraphicsEnvironment;
import java.math.BigDecimal;
import java.util.List;

import static br.com.scopel.pfv.beta.hardware.CardPaymentStatus.APPROVED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisabledIf("isHeadless")
class SplitPaymentScreenTests {

    private static final BigDecimal SALE_TOTAL = new BigDecimal("25.90");

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void toolkit() {
        FxTestSupport.startToolkit();
    }

    private static final class OpenScreen {
        final PaymentScreenFixture fixture = new PaymentScreenFixture();
        final SplitPaymentService splitPaymentService = mock(SplitPaymentService.class);
    }

    private OpenScreen open(ScriptedGateway gateway, List<Payment> registeredParts) throws Exception {
        OpenScreen screen = new OpenScreen();
        BigDecimal paid = registeredParts.stream().map(Payment::getAmountReceived)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        when(screen.splitPaymentService.status(any())).thenReturn(
                new SplitPaymentStatus(SALE_TOTAL, paid, SALE_TOTAL.subtract(paid), registeredParts));
        screen.fixture.load("pagamento_dividido.fxml", fixture -> new PagamentoDivididoController(
                screen.splitPaymentService, fixture.session, fixture.navigator, fixture.receiptService,
                gateway, mock(PixService.class)));
        return screen;
    }

    private Payment part(PaymentMethod method, CardType cardType, String amount) {
        Payment payment = new Payment();
        payment.setMethod(method);
        payment.setCardType(cardType);
        payment.setAmountReceived(new BigDecimal(amount));
        payment.setChange(BigDecimal.ZERO);
        return payment;
    }

    private void typeAmount(OpenScreen screen, String amount) throws Exception {
        screen.fixture.onFx(() -> ((TextField) screen.fixture.node("partAmountField")).setText(amount));
    }

    private SplitPaymentResult unfinished(OpenScreen screen, String remaining) {
        return new SplitPaymentResult(screen.fixture.sale, new Payment(), new BigDecimal(remaining),
                BigDecimal.ZERO, false);
    }

    private boolean isPart(PaymentInput input, PaymentMethod method, CardType cardType, String amount) {
        return input.method() == method && input.cardType() == cardType
                && input.amount().compareTo(new BigDecimal(amount)) == 0;
    }

    @Test
    void remainingAmountIsOfferedAsDefaultAndACashPartReturnsToTheMethodChoice() throws Exception {
        OpenScreen screen = open(new ScriptedGateway(false), List.of());
        when(screen.splitPaymentService.registerPart(any(), any())).thenReturn(unfinished(screen, "15.90"));

        screen.fixture.press(KeyCode.DIGIT1);
        assertEquals("25,90", screen.fixture.onFx(() -> ((TextField) screen.fixture.node("partAmountField")).getText()));
        typeAmount(screen, "10,00");
        screen.fixture.press(KeyCode.ENTER);

        verify(screen.splitPaymentService).registerPart(any(),
                argThat(input -> isPart(input, PaymentMethod.DINHEIRO, null, "10.00")));
        assertTrue(screen.fixture.statusContains("Falta pagar", 3));
    }

    @Test
    void theLastPartFinalizesTheSaleAndGoesToTheFinishedScreen() throws Exception {
        OpenScreen screen = open(new ScriptedGateway(false), List.of());
        when(screen.splitPaymentService.registerPart(any(), any())).thenReturn(new SplitPaymentResult(
                screen.fixture.sale, new Payment(), BigDecimal.ZERO, new BigDecimal("4.10"), true));

        screen.fixture.press(KeyCode.DIGIT1);
        typeAmount(screen, "30,00");
        screen.fixture.press(KeyCode.ENTER);

        verify(screen.fixture.navigator, timeout(3000)).navigate(Page.VENDA_FINALIZADA);
        verify(screen.fixture.receiptService, timeout(3000)).printSplitSaleReceipt(any(), any());
        assertEquals(null, screen.fixture.session.getUltimaForma());
        assertEquals(0, new BigDecimal("4.10").compareTo(screen.fixture.session.getUltimoTroco()));
        assertEquals(null, screen.fixture.session.getVendaAtual());
    }

    @Test
    void cardOrPixAboveTheRemainingAmountIsRefusedBeforeReachingTheService() throws Exception {
        OpenScreen screen = open(new ScriptedGateway(false), List.of());

        screen.fixture.press(KeyCode.DIGIT2);
        typeAmount(screen, "99,00");
        screen.fixture.press(KeyCode.ENTER);

        assertTrue(screen.fixture.statusContains("não pode passar do que falta", 3));
        verify(screen.splitPaymentService, never()).registerPart(any(), any());
    }

    @Test
    void repeatedEnterAfterAConfirmedPartDoesNotRegisterItTwice() throws Exception {
        OpenScreen screen = open(new ScriptedGateway(false), List.of());
        when(screen.splitPaymentService.registerPart(any(), any())).thenReturn(unfinished(screen, "15.90"));

        screen.fixture.press(KeyCode.DIGIT1);
        typeAmount(screen, "10,00");
        screen.fixture.press(KeyCode.ENTER);
        screen.fixture.press(KeyCode.ENTER);
        screen.fixture.press(KeyCode.ENTER);

        verify(screen.splitPaymentService, times(1)).registerPart(any(), any());
    }

    @Test
    void zeroOrEmptyAmountIsNotRegistered() throws Exception {
        OpenScreen screen = open(new ScriptedGateway(false), List.of());

        screen.fixture.press(KeyCode.DIGIT1);
        typeAmount(screen, "0");
        screen.fixture.press(KeyCode.ENTER);
        typeAmount(screen, "");
        screen.fixture.press(KeyCode.ENTER);

        verify(screen.splitPaymentService, never()).registerPart(any(), any());
        assertTrue(screen.fixture.statusContains("Informe o valor", 3));
    }

    @Test
    void goingBackIsBlockedWhenACardPartWasAlreadyReceived() throws Exception {
        OpenScreen screen = open(new ScriptedGateway(false),
                List.of(part(PaymentMethod.CARTAO, CardType.DEBITO, "10.00")));

        screen.fixture.press(KeyCode.ESCAPE);

        assertTrue(screen.fixture.statusContains("pagamento recebido", 3));
        verify(screen.fixture.navigator, never()).navigate(Page.PAGAMENTO);
        verify(screen.splitPaymentService, never()).removeCashPart(any(), any());
    }

    @Test
    void goingBackWithOnlyCashPartsDiscardsThemAndReturnsToTheMethodSelection() throws Exception {
        Payment cashPart = part(PaymentMethod.DINHEIRO, null, "10.00");
        OpenScreen screen = open(new ScriptedGateway(false), List.of(cashPart));
        when(screen.splitPaymentService.removeCashPart(any(), any())).thenReturn(
                new SplitPaymentStatus(SALE_TOTAL, BigDecimal.ZERO, SALE_TOTAL, List.of()));

        screen.fixture.press(KeyCode.ESCAPE);

        verify(screen.splitPaymentService).removeCashPart(any(), any());
        verify(screen.fixture.navigator).navigate(Page.PAGAMENTO);
    }

    @Test
    void approvedMachineChargeRegistersTheCardPartOnce() throws Exception {
        OpenScreen screen = open(new ScriptedGateway(true).script(APPROVED), List.of());
        when(screen.splitPaymentService.registerPart(any(), any())).thenReturn(unfinished(screen, "15.90"));

        screen.fixture.press(KeyCode.DIGIT2);
        typeAmount(screen, "10,00");
        screen.fixture.press(KeyCode.ENTER);

        verify(screen.splitPaymentService, timeout(12000)).registerPart(any(),
                argThat(input -> isPart(input, PaymentMethod.CARTAO, CardType.DEBITO, "10.00")));
        verify(screen.fixture.navigator, never()).navigate(Page.PAGAMENTO);
    }

    @Test
    void failureToRegisterAfterTheMachineApprovedKeepsAWarningAndRetriesOnEnter() throws Exception {
        OpenScreen screen = open(new ScriptedGateway(true).script(APPROVED), List.of());
        when(screen.splitPaymentService.registerPart(any(), any()))
                .thenThrow(new IllegalStateException("banco indisponível"))
                .thenReturn(unfinished(screen, "15.90"));

        screen.fixture.press(KeyCode.DIGIT3);
        typeAmount(screen, "10,00");
        screen.fixture.press(KeyCode.ENTER);

        assertTrue(screen.fixture.statusContainsIn("failureLabel", "PAGAMENTO RECEBIDO", 12));
        assertTrue(screen.fixture.onFx(() -> ((Label) screen.fixture.node("failureLabel")).isVisible()));

        screen.fixture.press(KeyCode.ENTER);

        verify(screen.splitPaymentService, timeout(3000).times(2)).registerPart(any(), any());
        assertTrue(screen.fixture.statusContains("Falta pagar", 3));
        assertTrue(!screen.fixture.onFx(() -> ((Label) screen.fixture.node("failureLabel")).isVisible()));
    }
}
