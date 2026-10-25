package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.AppSettingService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste puro (sem Spring): o comprovante de fechamento só usa getters do
 * CashRegister em memória. Mesmo estilo de {@link ReceiptAndLabelTests}
 * (PrinterPort noop + stub de AppSettingService).
 */
class CashCloseReceiptTests {

    private final PrinterPort noopPrinter = new PrinterPort() {
        @Override public void print(String content) { }
        @Override public void openDrawer() { }
    };

    private final AppSettingService settingsStub = new AppSettingService(null) {
        @Override public String getString(String key, String defaultValue) {
            return defaultValue;
        }
    };

    private CashRegister closedCashRegister() {
        User operator = new User();
        operator.setName("Operador Teste");

        CashRegister cr = new CashRegister();
        cr.setId(42L);
        cr.setOperator(operator);
        cr.setStatus(CashRegister.Status.CLOSED);
        cr.setOpeningAmount(new BigDecimal("100.00"));
        cr.setExpectedAmount(new BigDecimal("350.50"));
        cr.setClosingAmount(new BigDecimal("345.50"));
        cr.setDifference(new BigDecimal("-5.00"));   // faltou 5,00 no caixa
        cr.setOpenedAt(LocalDateTime.of(2026, 10, 20, 8, 0));
        cr.setClosedAt(LocalDateTime.of(2026, 10, 20, 18, 30));
        return cr;
    }

    @Test
    void cashCloseReceiptHasTitleAndAllAmountsWithCommaDecimal() {
        ReceiptService rs = new ReceiptService(settingsStub, noopPrinter);

        String receipt = rs.buildCashCloseReceipt(closedCashRegister());

        assertTrue(receipt.contains("FECHAMENTO DE CAIXA"), "título do comprovante");
        assertTrue(receipt.contains("Saldo inicial"), "linha saldo inicial");
        assertTrue(receipt.contains("Saldo esperado"), "linha saldo esperado");
        assertTrue(receipt.contains("Valor contado"), "linha valor contado");
        assertTrue(receipt.contains("Diferenca"), "linha diferença");

        // valores com vírgula decimal (RN01 / formatação money)
        assertTrue(receipt.contains("100,00"), "saldo inicial formatado");
        assertTrue(receipt.contains("350,50"), "saldo esperado formatado");
        assertTrue(receipt.contains("345,50"), "valor contado formatado");
        assertTrue(receipt.contains("-5,00"), "diferença negativa formatada");

        // não deve vazar ponto decimal nos valores monetários
        assertFalse(receipt.contains("100.00"), "não deve usar ponto decimal");
    }

    @Test
    void cashCloseReceiptHasNonFiscalWarning() {
        ReceiptService rs = new ReceiptService(settingsStub, noopPrinter);

        String receipt = rs.buildCashCloseReceipt(closedCashRegister());

        assertTrue(receipt.contains("NAO E DOCUMENTO FISCAL"), "aviso RNF15");
        assertTrue(receipt.contains("Operador Teste"), "nome do operador");
    }
}
