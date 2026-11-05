package br.com.scopel.pfv.beta.controller.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmountInputTests {

    private AmountInput typed(String keys) {
        AmountInput input = new AmountInput();
        for (char key : keys.toCharArray()) {
            if (key == ',') {
                input.comma();
            } else if (key == '<') {
                input.backspace();
            } else {
                input.digit(key);
            }
        }
        return input;
    }

    @Test
    void startsEmptyAndNotPositive() {
        AmountInput input = new AmountInput();

        assertEquals("0", input.display());
        assertFalse(input.isPositive());
    }

    @Test
    void typesWholeAndDecimalValues() {
        assertEquals(0, new BigDecimal("25.90").compareTo(typed("25,9").value()));
        assertEquals("25,9", typed("25,9").display());
        assertEquals(0, new BigDecimal("7.00").compareTo(typed("7").value()));
        assertEquals(0, new BigDecimal("0.50").compareTo(typed(",5").value()));
    }

    @Test
    void ignoresLeadingZerosAndExtraDecimals() {
        assertEquals("5", typed("005").display());
        assertEquals("1,25", typed("1,259").display());
        assertEquals("1,25", typed("1,2,5").display());
    }

    @Test
    void zeroIsNotPositive() {
        assertFalse(typed("0").isPositive());
        assertFalse(typed("0,00").isPositive());
        assertTrue(typed("0,01").isPositive());
    }

    @Test
    void backspaceRemovesDecimalsThenTheCommaThenIntegers() {
        AmountInput input = typed("12,5");

        input.backspace();
        assertEquals("12,", input.display());
        input.backspace();
        assertEquals("12", input.display());
        input.backspace();
        assertEquals("1", input.display());
    }

    @Test
    void limitsTheIntegerDigitsAndClears() {
        AmountInput input = typed("12345678");

        assertEquals("123456", input.display());
        input.clear();
        assertEquals("0", input.display());
    }
}
