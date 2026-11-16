package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import br.com.scopel.pfv.beta.service.refund.RefundPlan;
import br.com.scopel.pfv.beta.service.refund.SaleRefundBooking;
import br.com.scopel.pfv.beta.service.refund.SaleRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaleRefundServiceTests {

    private static final long SALE_ID = 7L;

    private SaleRepository sales;
    private PaymentRepository payments;
    private CardPaymentGateway gateway;
    private SaleRefundBooking booking;
    private SaleRefundService service;
    private Sale sale;
    private User manager;

    @BeforeEach
    void setUp() {
        sales = mock(SaleRepository.class);
        payments = mock(PaymentRepository.class);
        gateway = mock(CardPaymentGateway.class);
        booking = mock(SaleRefundBooking.class);
        service = new SaleRefundService(sales, payments, gateway, booking);
        CashRegister register = new CashRegister();
        register.setStatus(CashRegister.Status.OPEN);
        sale = new Sale();
        sale.setId(SALE_ID);
        sale.setStatus(Sale.Status.FINALIZADA);
        sale.setTotal(new BigDecimal("30.00"));
        sale.setCashRegister(register);
        when(sales.findById(SALE_ID)).thenReturn(Optional.of(sale));
        manager = new User();
        manager.setRole(User.Role.GERENTE);
        when(booking.complete(anyLong(), anyString(), any())).thenReturn(sale);
    }

    private Payment payment(long id, PaymentMethod method, String received, String change, String orderId) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setSale(sale);
        payment.setMethod(method);
        payment.setCardType(method == PaymentMethod.CARTAO ? CardType.DEBITO : null);
        payment.setAmountReceived(new BigDecimal(received));
        payment.setChange(new BigDecimal(change));
        payment.setGatewayOrderId(orderId);
        when(payments.findById(id)).thenReturn(Optional.of(payment));
        return payment;
    }

    private void salePayments(Payment... parts) {
        when(payments.findBySale_IdOrderByCreatedAtAscIdAsc(SALE_ID)).thenReturn(List.of(parts));
    }

    @Test
    void cashPlanShowsNetAmountToReturnWithoutChange() {
        salePayments(payment(1, PaymentMethod.DINHEIRO, "50.00", "20.00", null));

        RefundPlan plan = service.plan(SALE_ID);

        assertEquals(new BigDecimal("30.00"), plan.cashToReturn());
        assertTrue(plan.electronicParts().isEmpty());
    }

    @Test
    void operatorWithoutManagerAccessIsRefusedBeforeAnyRefund() {
        salePayments(payment(1, PaymentMethod.CARTAO, "30.00", "0.00", "ORD1"));
        User operator = new User();
        operator.setRole(User.Role.OPERADOR);

        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> service.refund(SALE_ID, "cliente testou", operator, false));

        verify(gateway, never()).refund(anyString());
        verify(booking, never()).complete(anyLong(), anyString(), any());
    }

    @Test
    void cardWithGatewayOrderIsRefundedOnTerminalAndSaleCompleted() {
        when(gateway.isIntegrated()).thenReturn(true);
        salePayments(payment(1, PaymentMethod.CARTAO, "30.00", "0.00", "ORD1"));

        service.refund(SALE_ID, "cliente testou o cartão", manager, false);

        verify(gateway).refund("ORD1");
        verify(booking).markPaymentRefunded(1L);
        verify(booking).complete(eq(SALE_ID), eq("cliente testou o cartão"), eq(manager));
    }

    @Test
    void cardWithoutGatewayOrderNeedsManualConfirmation() {
        when(gateway.isIntegrated()).thenReturn(true);
        salePayments(payment(1, PaymentMethod.CARTAO, "30.00", "0.00", null));

        assertThrows(IllegalArgumentException.class,
                () -> service.refund(SALE_ID, "cliente testou o cartão", manager, false));
        verify(booking, never()).complete(anyLong(), anyString(), any());

        service.refund(SALE_ID, "cliente testou o cartão", manager, true);

        verify(gateway, never()).refund(anyString());
        verify(booking).complete(eq(SALE_ID), anyString(), eq(manager));
    }

    @Test
    void terminalFailureLeavesTheSaleUntouched() {
        when(gateway.isIntegrated()).thenReturn(true);
        salePayments(payment(1, PaymentMethod.PIX, "30.00", "0.00", "ORD9"));
        doThrow(new CardGatewayException("prazo")).when(gateway).refund("ORD9");

        assertThrows(IllegalArgumentException.class,
                () -> service.refund(SALE_ID, "cliente desistiu", manager, false));

        verify(booking, never()).complete(anyLong(), anyString(), any());
    }

    @Test
    void reasonIsRequired() {
        salePayments(payment(1, PaymentMethod.DINHEIRO, "30.00", "0.00", null));

        assertThrows(RuntimeException.class, () -> service.refund(SALE_ID, "  ", manager, false));
        verify(booking, never()).complete(anyLong(), anyString(), any());
    }

    @Test
    void closedCashRegisterCannotBeRefunded() {
        sale.getCashRegister().setStatus(CashRegister.Status.CLOSED);

        assertThrows(IllegalArgumentException.class, () -> service.plan(SALE_ID));
    }

    @Test
    void creditSaleIsNotRefundableHere() {
        salePayments(payment(1, PaymentMethod.PRAZO, "30.00", "0.00", null));

        assertThrows(IllegalArgumentException.class, () -> service.plan(SALE_ID));
    }
}
