package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReceiptService {

    private static final int WIDTH = 40;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppSettingService appSettingService;
    private final PrinterPort printer;

    public ReceiptService(AppSettingService appSettingService, PrinterPort printer) {
        this.appSettingService = appSettingService;
        this.printer = printer;
    }

    public String buildSaleReceipt(Sale sale) {
        return buildSaleReceipt(sale, null);
    }

    public String buildSaleReceipt(Sale sale, ReceiptPayment payment) {
        String store = appSettingService.getString("store.name", "MERCADO");
        StringBuilder sb = new StringBuilder();
        sb.append(center(store)).append('\n');
        sb.append(center("CUPOM NAO FISCAL")).append('\n');
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
        appendPayment(sb, payment);
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
        appendMovements(sb, movements);
        sb.append(center("*** NAO E DOCUMENTO FISCAL ***"));
        return sb.toString();
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
