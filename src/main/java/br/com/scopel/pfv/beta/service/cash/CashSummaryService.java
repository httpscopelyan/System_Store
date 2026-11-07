package br.com.scopel.pfv.beta.service.cash;

import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CashSummaryService {

    private static final String ANONYMOUS_CUSTOMER = "CONSUMIDOR";

    private final CashRegisterService cashRegisterService;

    public CashSummaryService(CashRegisterService cashRegisterService) {
        this.cashRegisterService = cashRegisterService;
    }

    public CashOverview overview(CashRegister cashRegister) {
        Map<Long, SaleReceiptTotals> totalsBySale = receiptTotalsBySale(cashRegister);
        List<CashMovementRow> rows = new ArrayList<>();
        BigDecimal salesCash = BigDecimal.ZERO;
        BigDecimal pix = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal debit = BigDecimal.ZERO;

        for (Sale sale : cashRegisterService.sales(cashRegister)) {
            if (sale.getStatus() != Sale.Status.FINALIZADA) {
                continue;
            }
            SaleReceiptTotals totals = totalsBySale.getOrDefault(sale.getId(), new SaleReceiptTotals());
            salesCash = salesCash.add(totals.cash);
            pix = pix.add(totals.pix);
            credit = credit.add(totals.credit);
            debit = debit.add(totals.debit);
            rows.add(new CashMovementRow(sale.getCreatedAt(), true, totals.cash, totals.pix, totals.credit,
                    totals.debit, sale.getTotal(), sale.getId(),
                    sale.getCustomer() != null ? sale.getCustomer().getName() : ANONYMOUS_CUSTOMER, null));
        }

        BigDecimal supplements = BigDecimal.ZERO;
        BigDecimal withdrawals = BigDecimal.ZERO;
        for (CashMovement movement : cashRegisterService.movements(cashRegister)) {
            boolean entry = isEntry(movement);
            switch (movement.getType()) {
                case SUPRIMENTO -> supplements = supplements.add(movement.getAmount());
                case SANGRIA -> withdrawals = withdrawals.add(movement.getAmount());
                case RETIFICACAO -> {
                    if (movement.getCorrectedMovement().getType() == CashMovement.Type.SANGRIA) {
                        withdrawals = withdrawals.subtract(movement.getAmount());
                    } else {
                        supplements = supplements.add(movement.getAmount());
                    }
                }
            }
            rows.add(new CashMovementRow(movement.getCreatedAt(), entry, movement.getAmount(), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, movement.getAmount(), null, describe(movement), movement));
        }

        rows.sort(Comparator.comparing(CashMovementRow::at));
        return new CashOverview(rows, summarize(cashRegister, salesCash.add(supplements), pix, credit, debit, withdrawals));
    }

    private CashSummary summarize(CashRegister cashRegister, BigDecimal cashIn, BigDecimal pix,
                                  BigDecimal credit, BigDecimal debit, BigDecimal cashOut) {
        BigDecimal opening = cashRegister.getOpeningAmount() == null ? BigDecimal.ZERO : cashRegister.getOpeningAmount();
        BigDecimal totalIn = cashIn.add(pix).add(credit).add(debit);
        return new CashSummary(opening, cashIn, pix, credit, debit, totalIn, cashOut,
                opening.add(cashIn).subtract(cashOut));
    }

    private boolean isEntry(CashMovement movement) {
        return switch (movement.getType()) {
            case SUPRIMENTO -> true;
            case SANGRIA -> false;
            case RETIFICACAO -> movement.getAmount().signum() > 0;
        };
    }

    private String describe(CashMovement movement) {
        String base = movement.getType().name() + (movement.getReason() != null ? " - " + movement.getReason() : "");
        if (movement.getType() == CashMovement.Type.RETIFICACAO && movement.getCorrectedMovement() != null) {
            return base + " (corrige " + movement.getCorrectedMovement().getType().name() + " #"
                    + movement.getCorrectedMovement().getId() + ")";
        }
        return base;
    }

    private Map<Long, SaleReceiptTotals> receiptTotalsBySale(CashRegister cashRegister) {
        Map<Long, SaleReceiptTotals> totalsBySale = new HashMap<>();
        for (Payment payment : cashRegisterService.payments(cashRegister)) {
            BigDecimal net = nz(payment.getAmountReceived()).subtract(nz(payment.getChange()));
            SaleReceiptTotals totals = totalsBySale.computeIfAbsent(payment.getSale().getId(),
                    key -> new SaleReceiptTotals());
            if (payment.getMethod() == PaymentMethod.DINHEIRO) {
                totals.cash = totals.cash.add(net);
            } else if (payment.getMethod() == PaymentMethod.PIX) {
                totals.pix = totals.pix.add(net);
            } else if (payment.getMethod() == PaymentMethod.CARTAO) {
                if (payment.getCardType() == CardType.DEBITO) {
                    totals.debit = totals.debit.add(net);
                } else {
                    totals.credit = totals.credit.add(net);
                }
            }
        }
        return totalsBySale;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static final class SaleReceiptTotals {
        private BigDecimal cash = BigDecimal.ZERO;
        private BigDecimal pix = BigDecimal.ZERO;
        private BigDecimal credit = BigDecimal.ZERO;
        private BigDecimal debit = BigDecimal.ZERO;
    }
}
