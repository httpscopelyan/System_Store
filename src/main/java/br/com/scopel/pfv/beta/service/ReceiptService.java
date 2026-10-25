package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Monta e imprime o comprovante não-fiscal da venda (RF42). O texto traz
 * "NÃO É DOCUMENTO FISCAL" (RNF15). A formatação é pura e testável; a saída
 * física vai pela PrinterPort.
 */
@Service
public class ReceiptService {

    private static final int WIDTH = 40;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AppSettingService appSettingService;
    private final PrinterPort printer;

    public ReceiptService(AppSettingService appSettingService, PrinterPort printer) {
        this.appSettingService = appSettingService;
        this.printer = printer;
    }

    public String buildSaleReceipt(Sale sale) {
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
        sb.append(line()).append('\n');
        sb.append(center("*** NAO E DOCUMENTO FISCAL ***"));
        return sb.toString();
    }

    public void printSaleReceipt(Sale sale) {
        printer.print(buildSaleReceipt(sale));
    }

    /**
     * Monta o comprovante de fechamento de caixa (não-fiscal). Formatação pura
     * usando apenas os getters de {@link CashRegister}; a saída física vai pela
     * PrinterPort. Mantém o mesmo estilo de {@link #buildSaleReceipt}.
     */
    public String buildCashCloseReceipt(CashRegister cr) {
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
        sb.append(center("*** NAO E DOCUMENTO FISCAL ***"));
        return sb.toString();
    }

    public void printCashCloseReceipt(CashRegister cr) {
        printer.print(buildCashCloseReceipt(cr));
    }

    // ---- helpers de formatação ----

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
