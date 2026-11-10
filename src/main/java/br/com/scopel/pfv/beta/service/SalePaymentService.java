package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.ChangeNotAvailableException;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InsufficientPaymentException;
import br.com.scopel.pfv.beta.exception.InvalidDiscountException;
import br.com.scopel.pfv.beta.exception.PaymentMethodNotAvailableException;
import br.com.scopel.pfv.beta.exception.SplitPaymentInProgressException;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentInput;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SalePaymentService {

    private static final String SPLIT_PAYMENT_IN_PROGRESS = "Esta venda já tem parte do pagamento registrada."
            + " Conclua o pagamento dividido do valor restante.";

    private final PaymentRepository paymentRepository;
    private final SaleFinalizer saleFinalizer;

    public SalePaymentService(PaymentRepository paymentRepository, SaleFinalizer saleFinalizer) {
        this.paymentRepository = paymentRepository;
        this.saleFinalizer = saleFinalizer;
    }

    public BigDecimal calculateChange(BigDecimal total, BigDecimal amountReceived) {
        return amountReceived.subtract(total).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Sale pay(Sale sale, PaymentMethod method, BigDecimal amountReceived) {
        requireInPayment(sale);
        if (method != PaymentMethod.DINHEIRO) {
            throw new PaymentMethodNotAvailableException();
        }
        if (amountReceived.compareTo(sale.getTotal()) < 0) {
            throw new InsufficientPaymentException();
        }
        BigDecimal change = calculateChange(sale.getTotal(), amountReceived);
        savePayment(sale, method, null, amountReceived, change);
        return saleFinalizer.finalizeSale(sale);
    }

    @Transactional
    public Sale payCard(Sale sale, CardType cardType) {
        requireInPayment(sale);
        savePayment(sale, PaymentMethod.CARTAO, cardType, sale.getTotal(), noChange());
        return saleFinalizer.finalizeSale(sale);
    }

    @Transactional
    public Sale payPix(Sale sale) {
        requireInPayment(sale);
        savePayment(sale, PaymentMethod.PIX, null, sale.getTotal(), noChange());
        return saleFinalizer.finalizeSale(sale);
    }

    @Transactional
    public Sale finalizeOnCredit(Sale sale) {
        requireInPayment(sale);
        savePayment(sale, PaymentMethod.PRAZO, null, sale.getTotal(), noChange());
        return saleFinalizer.finalizeSale(sale);
    }

    @Transactional
    public Sale payMixed(Sale sale, List<PaymentInput> inputs) {
        requireInPayment(sale);
        if (inputs == null || inputs.isEmpty()) {
            throw new InsufficientPaymentException();
        }

        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        for (PaymentInput input : inputs) {
            if (input.method() == null || input.amount() == null || input.amount().signum() <= 0) {
                throw new InvalidDiscountException("valor de pagamento inválido");
            }
            BigDecimal amount = input.amount().setScale(2, RoundingMode.HALF_UP);
            sum = sum.add(amount);
            if (input.method() == PaymentMethod.DINHEIRO) {
                cash = cash.add(amount);
            }
        }

        if (sum.compareTo(sale.getTotal()) < 0) {
            throw new InsufficientPaymentException();
        }
        BigDecimal change = sum.subtract(sale.getTotal()).setScale(2, RoundingMode.HALF_UP);
        if (change.signum() > 0 && cash.compareTo(change) < 0) {
            throw new ChangeNotAvailableException();
        }

        boolean changeAssigned = false;
        for (PaymentInput input : inputs) {
            BigDecimal thisChange = noChange();
            if (!changeAssigned && input.method() == PaymentMethod.DINHEIRO && change.signum() > 0) {
                thisChange = change;
                changeAssigned = true;
            }
            CardType cardType = input.method() == PaymentMethod.CARTAO ? input.cardType() : null;
            savePayment(sale, input.method(), cardType, input.amount().setScale(2, RoundingMode.HALF_UP),
                    thisChange);
        }
        return saleFinalizer.finalizeSale(sale);
    }

    private void requireInPayment(Sale sale) {
        if (sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }
        if (sale.getId() != null && paymentRepository.existsBySale_Id(sale.getId())) {
            throw new SplitPaymentInProgressException(SPLIT_PAYMENT_IN_PROGRESS);
        }
    }

    private BigDecimal noChange() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private void savePayment(Sale sale, PaymentMethod method, CardType cardType, BigDecimal amountReceived,
                             BigDecimal change) {
        Payment payment = new Payment();
        payment.setSale(sale);
        payment.setMethod(method);
        payment.setCardType(cardType);
        payment.setAmountReceived(amountReceived);
        payment.setChange(change);
        payment.setCreatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
    }
}
