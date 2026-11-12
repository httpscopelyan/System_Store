package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.PaymentScreenFixture.ScriptedGateway;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.controller.PagamentoEsperaController;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.ReceiptPayment;
import javafx.scene.input.KeyCode;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.awt.GraphicsEnvironment;

import static br.com.scopel.pfv.beta.hardware.CardPaymentStatus.APPROVED;
import static br.com.scopel.pfv.beta.hardware.CardPaymentStatus.CANCELED;
import static br.com.scopel.pfv.beta.hardware.CardPaymentStatus.DECLINED;
import static br.com.scopel.pfv.beta.hardware.CardPaymentStatus.PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisabledIf("isHeadless")
class CardChargeScreenTests {

    private static final int POLL_WAIT = 12;

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @BeforeAll
    static void toolkit() {
        FxTestSupport.startToolkit();
    }

    private PaymentScreenFixture open(ScriptedGateway gateway) throws Exception {
        PaymentScreenFixture screen = new PaymentScreenFixture();
        screen.load("pagamento_espera.fxml", fixture -> new PagamentoEsperaController(fixture.saleService,
                fixture.session, fixture.navigator, fixture.receiptService, gateway));
        when(screen.saleService.payCard(any(), any())).thenReturn(screen.sale);
        return screen;
    }

    @Test
    void customerPaysOnTheMachineAndTheSaleIsRegisteredOnceWithoutLeavingThroughSelection() throws Exception {
        PaymentScreenFixture screen = open(new ScriptedGateway(true).script(PENDING, APPROVED));

        verify(screen.saleService, timeout(POLL_WAIT * 1000)).payCard(screen.sale, CardType.DEBITO);
        verify(screen.navigator, timeout(2000)).navigate(Page.VENDA_FINALIZADA);
        verify(screen.navigator, never()).navigate(Page.PAGAMENTO);
        verify(screen.navigator, atLeastOnce()).unlock();
        assertEquals(PaymentMethod.CARTAO, screen.session.getUltimaForma());
        assertEquals(null, screen.session.getVendaAtual());
    }

    @Test
    void navigationIsLockedWhileTheChargeIsOpenSoNoShortcutAbandonsIt() throws Exception {
        PaymentScreenFixture screen = open(new ScriptedGateway(true).script(PENDING));

        verify(screen.navigator).lock(argThat(reason -> reason.contains("ESC")));
        verify(screen.navigator, never()).unlock();
    }

    @Test
    void escapeAsksForConfirmationAndKeepingTheChargeChangesNothing() throws Exception {
        ScriptedGateway gateway = new ScriptedGateway(true).script(PENDING, PENDING, APPROVED);
        PaymentScreenFixture screen = open(gateway);
        screen.answerCancellation(false);

        screen.press(KeyCode.ESCAPE);

        assertEquals(0, gateway.cancels.get(), "operador desistiu de cancelar");
        verify(screen.navigator, never()).navigate(Page.PAGAMENTO);
        verify(screen.saleService, timeout(POLL_WAIT * 1000)).payCard(screen.sale, CardType.DEBITO);
    }

    @Test
    void confirmingTheCancellationCancelsOnTheMachineAndReturnsToSelectionWithoutRegisteringTheSale() throws Exception {
        ScriptedGateway gateway = new ScriptedGateway(true).script(PENDING);
        PaymentScreenFixture screen = open(gateway);
        Thread.sleep(300);
        screen.answerCancellation(true);

        screen.press(KeyCode.ESCAPE);

        verify(screen.navigator, timeout(5000)).navigate(Page.PAGAMENTO);
        assertEquals(1, gateway.cancels.get());
        verify(screen.saleService, never()).payCard(any(), any());
    }

    @Test
    void cancelClickedAfterTheCustomerAlreadyPaidRegistersTheSaleInsteadOfLosingIt() throws Exception {
        ScriptedGateway gateway = new ScriptedGateway(true).script(PENDING, APPROVED).cancelFails();
        PaymentScreenFixture screen = open(gateway);
        Thread.sleep(300);
        screen.answerCancellation(true);

        screen.press(KeyCode.ESCAPE);

        verify(screen.saleService, timeout(POLL_WAIT * 1000)).payCard(screen.sale, CardType.DEBITO);
        verify(screen.navigator, timeout(2000)).navigate(Page.VENDA_FINALIZADA);
        verify(screen.navigator, never()).navigate(Page.PAGAMENTO);
    }

    @Test
    void declinedCardStaysOnScreenUntilTheOperatorPressesEnter() throws Exception {
        PaymentScreenFixture screen = open(new ScriptedGateway(true).script(DECLINED, DECLINED));

        assertTrue(screen.statusContains("Nenhum valor foi registrado", POLL_WAIT));
        Thread.sleep(3000);
        verify(screen.navigator, never()).navigate(Page.PAGAMENTO);

        screen.press(KeyCode.ENTER);

        verify(screen.navigator, timeout(2000)).navigate(Page.PAGAMENTO);
        verify(screen.saleService, never()).payCard(any(), any());
    }

    @Test
    void machineCanceledChargeAlsoWaitsForTheOperator() throws Exception {
        PaymentScreenFixture screen = open(new ScriptedGateway(true).script(CANCELED, CANCELED));

        assertTrue(screen.statusContains("cancelada na maquininha", POLL_WAIT));
        Thread.sleep(3000);
        verify(screen.navigator, never()).navigate(Page.PAGAMENTO);
    }

    @Test
    void aSingleDeclinedReadingFollowedByApprovalStillRegistersTheSale() throws Exception {
        PaymentScreenFixture screen = open(new ScriptedGateway(true).script(DECLINED, APPROVED));

        verify(screen.saleService, timeout(POLL_WAIT * 1000)).payCard(screen.sale, CardType.DEBITO);
        verify(screen.navigator, never()).navigate(Page.PAGAMENTO);
    }

    @Test
    void ifSavingTheSaleFailsAfterPaymentTheOperatorRetriesWithEnterAndTheCustomerIsNotChargedAgain() throws Exception {
        ScriptedGateway gateway = new ScriptedGateway(true).script(APPROVED);
        PaymentScreenFixture screen = new PaymentScreenFixture();
        when(screen.saleService.payCard(any(), any())).thenThrow(new IllegalSaleStateException())
                .thenReturn(screen.sale);
        screen.load("pagamento_espera.fxml", fixture -> new PagamentoEsperaController(fixture.saleService,
                fixture.session, fixture.navigator, fixture.receiptService, gateway));

        assertTrue(screen.statusContains("PAGAMENTO RECEBIDO", POLL_WAIT));
        verify(screen.navigator, never()).navigate(Page.VENDA_FINALIZADA);

        screen.press(KeyCode.ENTER);

        verify(screen.saleService, timeout(3000).times(2)).payCard(screen.sale, CardType.DEBITO);
        verify(screen.navigator, timeout(2000)).navigate(Page.VENDA_FINALIZADA);
        assertEquals(1, gateway.charges.get(), "a maquininha só pode ser cobrada uma vez");
    }

    @Test
    void enterWhileWaitingForTheMachineDoesNotRegisterAnything() throws Exception {
        PaymentScreenFixture screen = open(new ScriptedGateway(true).script(PENDING));

        screen.press(KeyCode.ENTER);
        screen.press(KeyCode.ENTER);

        verify(screen.saleService, never()).payCard(any(), any());
        verify(screen.navigator, never()).navigate(any());
    }

    @Test
    void approvedCardSaleDoesNotPrintTheReceiptAutomatically() throws Exception {
        PaymentScreenFixture screen = new PaymentScreenFixture();
        screen.session.setCardTypeSelecionado(CardType.CREDITO);
        when(screen.saleService.payCard(any(), any())).thenReturn(screen.sale);
        ScriptedGateway gateway = new ScriptedGateway(true).script(APPROVED);
        screen.load("pagamento_espera.fxml", fixture -> new PagamentoEsperaController(fixture.saleService,
                fixture.session, fixture.navigator, fixture.receiptService, gateway));

        verify(screen.navigator, timeout(POLL_WAIT * 1000)).navigate(Page.VENDA_FINALIZADA);
        verify(screen.receiptService, never()).printSaleReceipt(any(Sale.class), any(ReceiptPayment.class));
    }

    @Test
    void manualModeRejectsAWrongAmountAndAcceptsTheExactOneWithoutLockingNavigation() throws Exception {
        PaymentScreenFixture screen = open(new ScriptedGateway(false));
        verify(screen.navigator, never()).lock(anyString());

        screen.onFx(() -> ((TextField) screen.node("receivedField")).setText("10,00"));
        screen.press(KeyCode.ENTER);
        assertTrue(screen.status().contains("não confere"));
        verify(screen.saleService, never()).payCard(any(), any());

        screen.onFx(() -> ((TextField) screen.node("receivedField")).setText("25,90"));
        screen.press(KeyCode.ENTER);

        verify(screen.saleService, times(1)).payCard(screen.sale, CardType.DEBITO);
        verify(screen.navigator).navigate(Page.VENDA_FINALIZADA);
        assertFalse(screen.session.getUltimaVenda() == null);
    }
}
