package br.com.scopel.pfv.beta.service.refund;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.hardware.CardPaymentGateway;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import br.com.scopel.pfv.beta.service.MovementReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class SaleRefundService {

    static final int MAX_REASON_LENGTH = 150;
    static final String CREDIT_NOT_SUPPORTED =
            "Venda a prazo não pode ser reembolsada aqui. Cancele o título em Contas a Receber.";
    static final String CASH_CLOSED = "Só é possível reembolsar vendas de um caixa aberto.";
    static final String MANUAL_NOT_CONFIRMED =
            "Confirme que o valor em cartão/PIX foi devolvido no aplicativo do Mercado Pago.";

    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;
    private final CardPaymentGateway gateway;
    private final SaleRefundBooking booking;

    public SaleRefundService(SaleRepository saleRepository, PaymentRepository paymentRepository,
                             CardPaymentGateway gateway, SaleRefundBooking booking) {
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.booking = booking;
    }

    @Transactional(readOnly = true)
    public RefundPlan plan(Long saleId) {
        Sale sale = refundableSale(saleId);
        BigDecimal cash = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<RefundPlan.ElectronicPart> electronic = new ArrayList<>();
        for (Payment payment : paymentRepository.findBySale_IdOrderByCreatedAtAscIdAsc(saleId)) {
            if (payment.getMethod() == PaymentMethod.PRAZO) {
                throw new IllegalArgumentException(CREDIT_NOT_SUPPORTED);
            }
            BigDecimal net = payment.getAmountReceived().subtract(payment.getChange());
            if (payment.getMethod() == PaymentMethod.DINHEIRO) {
                cash = cash.add(net);
                continue;
            }
            boolean automatic = gateway.isIntegrated() && payment.getGatewayOrderId() != null;
            electronic.add(new RefundPlan.ElectronicPart(payment.getId(), payment.getMethod(), payment.getCardType(),
                    net, automatic, payment.getRefundedAt() != null));
        }
        return new RefundPlan(saleId, sale.getTotal(), cash, electronic);
    }

    public Sale refund(Long saleId, String reason, User manager, boolean manualRefundConfirmed) {
        if (manager == null || !manager.hasManagerAccess()) {
            throw new ManagerAuthorizationRequiredException();
        }
        String normalizedReason = MovementReason.require(reason, MAX_REASON_LENGTH);
        RefundPlan plan = plan(saleId);
        if (plan.needsManualConfirmation() && !manualRefundConfirmed) {
            throw new IllegalArgumentException(MANUAL_NOT_CONFIRMED);
        }
        for (RefundPlan.ElectronicPart part : plan.electronicParts()) {
            if (part.automatic() && !part.alreadyRefunded()) {
                refundOnTerminal(part);
            }
        }
        return booking.complete(saleId, normalizedReason, manager);
    }

    private void refundOnTerminal(RefundPlan.ElectronicPart part) {
        Payment payment = paymentRepository.findById(part.paymentId()).orElseThrow(IllegalSaleStateException::new);
        try {
            gateway.refund(payment.getGatewayOrderId());
        } catch (CardGatewayException failure) {
            throw new IllegalArgumentException("Reembolso no Mercado Pago falhou: " + failure.getMessage()
                    + " Nada foi alterado na venda.");
        }
        booking.markPaymentRefunded(part.paymentId());
    }

    private Sale refundableSale(Long saleId) {
        Sale sale = saleRepository.findById(saleId).orElseThrow(IllegalSaleStateException::new);
        if (sale.getStatus() != Sale.Status.FINALIZADA) {
            throw new IllegalSaleStateException();
        }
        if (sale.getCashRegister() == null || sale.getCashRegister().getStatus() != CashRegister.Status.OPEN) {
            throw new IllegalArgumentException(CASH_CLOSED);
        }
        return sale;
    }
}
