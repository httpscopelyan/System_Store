package br.com.scopel.pfv.beta;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Item B (QA / Time de Validação): regra de exibição de QUANTIDADE sem zeros à
 * direita usada por {@code EstoqueController.formatQuantity} e
 * {@code EditProductController.formatQuantity}.
 *
 * <p>Os métodos de produção são {@code private} em controllers JavaFX (@FXML),
 * difíceis de instanciar em teste unitário. Os dois usam a MESMA expressão
 * {@code v == null ? "" : v.stripTrailingZeros().toPlainString()}. Este teste
 * trava o contrato dessa expressão (regressão + documentação) e confirma as
 * bordas críticas no runtime real (JDK 26):
 * <ul>
 *   <li>32000 (escala 0) → "32000" e NÃO "32" (risco do stripTrailingZeros
 *       virar 3.2E+4);</li>
 *   <li>0.000 → "0" e não "0E-3" (bug histórico de BigDecimal já corrigido no
 *       JDK em uso);</li>
 *   <li>null → "".</li>
 * </ul>
 * Dinheiro (custo/preço) usa {@code toPlainString()} SEM stripping — fora desta
 * regra; verificado por inspeção (colunas/campos usam {@code plain(...)}).
 */
class FormatQuantityRuleTests {

    /** Cópia fiel da expressão de produção, para travar o comportamento. */
    private static String formatQuantity(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    @Test
    void integerStockKeepsAllDigits() {
        // o caso de risco: 32000 não pode virar "32"
        assertEquals("32000", formatQuantity(new BigDecimal("32000")));
    }

    @Test
    void trailingFractionalZerosAreStripped() {
        assertEquals("32", formatQuantity(new BigDecimal("32.000")));
        assertEquals("1.25", formatQuantity(new BigDecimal("1.250")));
        assertEquals("100.5", formatQuantity(new BigDecimal("100.500")));
    }

    @Test
    void zeroIsPlainZero() {
        assertEquals("0", formatQuantity(new BigDecimal("0")));
        assertEquals("0", formatQuantity(new BigDecimal("0.000")));
    }

    @Test
    void nullBecomesEmptyString() {
        assertEquals("", formatQuantity(null));
    }

    @Test
    void moneyRuleIsDifferent_plainStringKeepsScaleNoStripping() {
        // Dinheiro NÃO sofre stripping: 1.50 permanece "1.50" via toPlainString().
        assertEquals("1.50", new BigDecimal("1.50").toPlainString());
        // (se passasse por formatQuantity viraria "1.5" — por isso dinheiro usa plain())
        assertEquals("1.5", formatQuantity(new BigDecimal("1.50")));
    }
}
