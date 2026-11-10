package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.service.cash.CashMovementRow;
import br.com.scopel.pfv.beta.service.cash.CashOverview;
import br.com.scopel.pfv.beta.service.cash.CashSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReceiptService {

    private static final int WIDTH = 40;
    private static final String EXTRA_NOTE_LABEL = "Obs. extra: ";
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppSettingService appSettingService;
    private final PrinterPort printer;

    public ReceiptService(AppSettingService appSettingService, PrinterPort printer) {
        this.appSettingService = appSettingService;
        this.printer = printer;
    }

    public String buildSaleReceipt(Sale sale) {
        return buildSaleReceipt(sale, List.of(), false);
    }

    public String buildSaleReceipt(Sale sale, ReceiptPayment payment) {
        return buildSaleReceipt(sale, singlePayment(payment), false);
    }

    public String buildSaleReceiptCopy(Sale sale, ReceiptPayment payment) {
        return buildSaleReceipt(sale, singlePayment(payment), true);
    }

    public void printSaleReceiptCopy(Sale sale, ReceiptPayment payment) {
        printer.print(buildSaleReceiptCopy(sale, payment));
    }

    public String buildSplitSaleReceipt(Sale sale, List<ReceiptPayment> payments) {
        return buildSaleReceipt(sale, payments, false);
    }

    public String buildSplitSaleReceiptCopy(Sale sale, List<ReceiptPayment> payments) {
        return buildSaleReceipt(sale, payments, true);
    }

    public void printSplitSaleReceipt(Sale sale, List<ReceiptPayment> payments) {
        printer.print(buildSplitSaleReceipt(sale, payments));
    }

    public void printSplitSaleReceiptCopy(Sale sale, List<ReceiptPayment> payments) {
        printer.print(buildSplitSaleReceiptCopy(sale, payments));
    }

    private List<ReceiptPayment> singlePayment(ReceiptPayment payment) {
        return payment == null ? List.of() : List.of(payment);
    }

    private String buildSaleReceipt(Sale sale, List<ReceiptPayment> payments, boolean copy) {
        String store = appSettingService.getString("store.name", "MERCADO");
        StringBuilder sb = new StringBuilder();
        sb.append(center(store)).append('\n');
        sb.append(center("CUPOM NAO FISCAL")).append('\n');
        if (copy) {
            sb.append(center("*** 2a VIA ***")).append('\n');
        }
        if (sale.getCreatedAt() != null) {
            sb.append(DT.format(sale.getCreatedAt())).append('\n');
        }
        sb.append("Venda #").append(sale.getId() == null ? "-" : sale.getId()).append('\n');
        sb.append(line()).append('\n');

        for (SaleItem item : sale.getItems()) {
            String desc = item.getProduct().getDescription();
            sb.append(clip(desc, WIDTH)).append('\n');
            String calc = strip(item.getQuantity()) + " x " + money(item.getUnitPrice());
            sb.append(leftRight(calc, money(item.getTotalPrice()))).append('\n');
        }

        sb.append(line()).append('\n');
        if (sale.getDiscount() != null && sale.getDiscount().signum() > 0) {
            sb.append(leftRight("Desconto", "-" + money(sale.getDiscount()))).append('\n');
        }
        if (sale.getSurcharge() != null && sale.getSurcharge().signum() > 0) {
            sb.append(leftRight("Acrescimo", "+" + money(sale.getSurcharge()))).append('\n');
        }
        sb.append(leftRight("TOTAL", money(sale.getTotal()))).append('\n');
        appendPayments(sb, payments);
        sb.append(line()).append('\n');
        sb.append(center("*** NAO E DOCUMENTO FISCAL ***"));
        return sb.toString();
    }

    public String buildPrinterTest() {
        String store = appSettingService.getString("store.name", "MERCADO");
        StringBuilder sb = new StringBuilder();
        sb.append(center(store)).append('\n');
        sb.append(center("TESTE DE IMPRESSAO")).append('\n');
        sb.append(DT.format(java.time.LocalDateTime.now())).append('\n');
        sb.append(line()).append('\n');
        sb.append("1234567890123456789012345678901234567890").append('\n');
        sb.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklm").append('\n');
        sb.append(leftRight("Item de exemplo", money(new BigDecimal("12.50")))).append('\n');
        sb.append(leftRight("TOTAL", money(new BigDecimal("12.50")))).append('\n');
        sb.append(line()).append('\n');
        sb.append(center("Se voce le isto, a impressora")).append('\n');
        sb.append(center("esta funcionando.")).append('\n');
        sb.append(center("*** NAO E DOCUMENTO FISCAL ***"));
        return sb.toString();
    }

    public void printPrinterTest() {
        printer.print(buildPrinterTest());
    }

    public void printSaleReceipt(Sale sale) {
        printer.print(buildSaleReceipt(sale));
    }

    public void printSaleReceipt(Sale sale, ReceiptPayment payment) {
        printer.print(buildSaleReceipt(sale, payment));
    }

    private void appendPayments(StringBuilder sb, List<ReceiptPayment> payments) {
        if (payments == null || payments.isEmpty()) {
            return;
        }
        if (payments.size() == 1) {
            appendPayment(sb, payments.get(0));
            return;
        }
        sb.append(center("PAGAMENTO DIVIDIDO")).append('\n');
        BigDecimal totalChange = BigDecimal.ZERO;
        for (ReceiptPayment payment : payments) {
            if (payment == null || payment.method() == null) {
                continue;
            }
            sb.append(leftRight(paymentName(payment), money(payment.received()))).append('\n');
            totalChange = totalChange.add(payment.change() == null ? BigDecimal.ZERO : payment.change());
        }
        if (totalChange.signum() > 0) {
            sb.append(leftRight("Troco", money(totalChange))).append('\n');
        }
    }

    private void appendPayment(StringBuilder sb, ReceiptPayment payment) {
        if (payment == null || payment.method() == null) {
            return;
        }
        sb.append(leftRight("Pagamento", paymentName(payment))).append('\n');
        if (payment.method() == PaymentMethod.DINHEIRO && payment.received() != null) {
            sb.append(leftRight("Recebido", money(payment.received()))).append('\n');
            sb.append(leftRight("Troco", money(payment.change()))).append('\n');
        }
    }

    private String paymentName(ReceiptPayment payment) {
        return switch (payment.method()) {
            case DINHEIRO -> "Dinheiro";
            case PIX -> "PIX";
            case PRAZO -> "A Prazo";
            case CARTAO -> payment.cardType() == CardType.DEBITO ? "Cartao de Debito"
                    : payment.cardType() == CardType.CREDITO ? "Cartao de Credito" : "Cartao";
        };
    }

    public String buildCashCloseReceipt(CashRegister cr) {
        return buildCashCloseReceipt(cr, List.of());
    }

    public String buildCashCloseReceipt(CashRegister cr, List<CashMovement> movements) {
        String store = appSettingService.getString("store.name", "MERCADO");
        StringBuilder sb = new StringBuilder();
        sb.append(center(store)).append('\n');
        sb.append(center("FECHAMENTO DE CAIXA")).append('\n');
        sb.append("Caixa #").append(cr.getId() == null ? "-" : cr.getId()).append('\n');
        if (cr.getOperator() != null) {
            sb.append("Operador: ").append(clip(cr.getOperator().getName(), WIDTH - 10)).append('\n');
        }
        if (cr.getOpenedAt() != null) {
            sb.append("Abertura:   ").append(DT.format(cr.getOpenedAt())).append('\n');
        }
        if (cr.getClosedAt() != null) {
            sb.append("Fechamento: ").append(DT.format(cr.getClosedAt())).append('\n');
        }
        sb.append(line()).append('\n');
        sb.append(leftRight("Saldo inicial", money(cr.getOpeningAmount()))).append('\n');
        sb.append(leftRight("Saldo esperado", money(cr.getExpectedAmount()))).append('\n');
        sb.append(leftRight("Valor contado", money(cr.getClosingAmount()))).append('\n');
        sb.append(leftRight("Diferenca", money(cr.getDifference()))).append('\n');
        sb.append(line()).append('\n');
        appendClosingComparison(sb, cr);
        appendMovements(sb, movements);
        sb.append(center("*** NAO E DOCUMENTO FISCAL ***"));
        return sb.toString();
    }

    public String buildCashMovementsReceipt(CashRegister cr, CashOverview overview) {
        String store = appSettingService.getString("store.name", "MERCADO");
        StringBuilder sb = new StringBuilder();
        sb.append(center(store)).append('\n');
        sb.append(center("MOVIMENTACAO DO CAIXA")).append('\n');
        sb.append("Caixa #").append(cr.getId() == null ? "-" : cr.getId()).append('\n');
        if (cr.getOperator() != null) {
            sb.append("Operador: ").append(clip(cr.getOperator().getName(), WIDTH - 10)).append('\n');
        }
        if (cr.getOpenedAt() != null) {
            sb.append("Abertura:   ").append(DT.format(cr.getOpenedAt())).append('\n');
        }
        sb.append("Impresso:   ").append(DT.format(java.time.LocalDateTime.now())).append('\n');
        sb.append(line()).append('\n');
        for (CashMovementRow row : overview.rows()) {
            String when = row.at() == null ? "" : TIME.format(row.at()) + " ";
            String what = row.saleId() != null ? "Venda #" + row.saleId() : row.history();
            sb.append(leftRight(when + clip(what, 22), (row.entry() ? "+" : "-") + money(row.total()))).append('\n');
        }
        sb.append(line()).append('\n');
        CashSummary summary = overview.summary();
        sb.append(leftRight("Saldo inicial", money(summary.opening()))).append('\n');
        sb.append(leftRight("Entrada dinheiro", money(summary.cashIn()))).append('\n');
        sb.append(leftRight("Entrada PIX", money(summary.pix()))).append('\n');
        sb.append(leftRight("Entrada credito", money(summary.credit()))).append('\n');
        sb.append(leftRight("Entrada debito", money(summary.debit()))).append('\n');
        sb.append(leftRight("Entrada extra", money(summary.extra()))).append('\n');
        sb.append(leftRight("Saidas dinheiro", money(summary.cashOut()))).append('\n');
        sb.append(leftRight("Saldo em dinheiro", money(summary.finalBalance()))).append('\n');
        sb.append(line()).append('\n');
        sb.append(center("*** NAO E DOCUMENTO FISCAL ***"));
        return sb.toString();
    }

    public void printCashMovementsReceipt(CashRegister cr, CashOverview overview) {
        printer.print(buildCashMovementsReceipt(cr, overview));
    }

    private void appendClosingComparison(StringBuilder sb, CashRegister cr) {
        if (cr.getExpectedPix() == null) {
            return;
        }
        sb.append(center("CONFERENCIA POR FORMA")).append('\n');
        sb.append(columns("", "Esperado", "Contado", "Difer.")).append('\n');
        BigDecimal total = appendComparisonRow(sb, "Dinheiro", cr.getExpectedAmount(), cr.getClosingAmount());
        total = total.add(appendComparisonRow(sb, "PIX", cr.getExpectedPix(), cr.getCountedPix()));
        total = total.add(appendComparisonRow(sb, "Credito", cr.getExpectedCredit(), cr.getCountedCredit()));
        total = total.add(appendComparisonRow(sb, "Debito", cr.getExpectedDebit(), cr.getCountedDebit()));
        if (cr.getExpectedExtra() != null) {
            total = total.add(appendComparisonRow(sb, "Extra", cr.getExpectedExtra(), cr.getCountedExtra()));
        }
        appendExtraNote(sb, cr);
        sb.append(line()).append('\n');
        String verdict = total.signum() == 0 ? "CAIXA CONFERE"
                : total.signum() < 0 ? "FALTA " + money(total.negate()) : "SOBRA " + money(total);
        sb.append(leftRight("DIFERENCA TOTAL", money(total))).append('\n');
        sb.append(center(verdict)).append('\n');
        sb.append(line()).append('\n');
    }

    private void appendExtraNote(StringBuilder sb, CashRegister cr) {
        if (cr.getExtraNote() == null || cr.getExtraNote().isBlank()) {
            return;
        }
        String note = EXTRA_NOTE_LABEL + cr.getExtraNote().trim();
        for (int start = 0; start < note.length(); start += WIDTH) {
            sb.append(note, start, Math.min(note.length(), start + WIDTH)).append('\n');
        }
    }

    private BigDecimal appendComparisonRow(StringBuilder sb, String label, BigDecimal expected, BigDecimal counted) {
        BigDecimal difference = (counted == null ? BigDecimal.ZERO : counted)
                .subtract(expected == null ? BigDecimal.ZERO : expected);
        sb.append(columns(label, number(expected), number(counted), number(difference))).append('\n');
        return difference;
    }

    private String columns(String label, String expected, String counted, String difference) {
        return String.format("%-9s%10s%10s%10s", clip(label, 9), clip(expected, 10), clip(counted, 10),
                clip(difference, 10));
    }

    private String number(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',');
    }

    public void printCashCloseReceipt(CashRegister cr) {
        printer.print(buildCashCloseReceipt(cr));
    }

    public void printCashCloseReceipt(CashRegister cr, List<CashMovement> movements) {
        printer.print(buildCashCloseReceipt(cr, movements));
    }

    private void appendMovements(StringBuilder sb, List<CashMovement> movements) {
        if (movements == null || movements.isEmpty()) {
            return;
        }
        sb.append(center("MOVIMENTOS DO CAIXA")).append('\n');
        for (CashMovement movement : movements) {
            String when = movement.getCreatedAt() == null ? "" : " " + TIME.format(movement.getCreatedAt());
            sb.append(leftRight(movement.getType().name() + when, money(movement.getAmount().setScale(2, RoundingMode.HALF_UP)))).append('\n');
            if (movement.getReason() != null && !movement.getReason().isBlank()) {
                sb.append("  ").append(clip(movement.getReason(), WIDTH - 2)).append('\n');
            }
        }
        sb.append(line()).append('\n');
    }

    private String money(BigDecimal value) {
        return "R$ " + (value == null ? "0,00" : value.toPlainString().replace('.', ','));
    }

    private String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private String center(String text) {
        text = clip(text, WIDTH);
        int pad = (WIDTH - text.length()) / 2;
        return " ".repeat(Math.max(0, pad)) + text;
    }

    private String leftRight(String left, String right) {
        left = clip(left, WIDTH);
        right = clip(right, WIDTH);
        int space = WIDTH - left.length() - right.length();
        return left + " ".repeat(Math.max(1, space)) + right;
    }

    private String line() {
        return "-".repeat(WIDTH);
    }
}
