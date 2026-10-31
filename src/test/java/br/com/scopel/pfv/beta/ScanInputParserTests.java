package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.controller.support.ScanInputParser;
import br.com.scopel.pfv.beta.exception.InvalidQuantityException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanInputParserTests {

    @Test
    void plainCodeHasNoQuantity_RN05() {
        ScanInputParser.ParsedScan parsed = ScanInputParser.parse("  0012345  ");
        assertEquals("0012345", parsed.code());
        assertFalse(parsed.hasQuantity());
    }

    @Test
    void textCodeIsKeptAsTyped_RF70() {
        assertEquals("pao frances", ScanInputParser.parse("pao frances").code());
    }

    @Test
    void quantityTimesCodeSplitsOnTheFirstStar_RN05() {
        ScanInputParser.ParsedScan parsed = ScanInputParser.parse("5*00123");
        assertEquals("00123", parsed.code());
        assertEquals(0, new BigDecimal("5").compareTo(parsed.quantity()));
    }

    @Test
    void decimalQuantityAcceptsComma_RN05() {
        ScanInputParser.ParsedScan parsed = ScanInputParser.parse("0,750*carne");
        assertEquals(0, new BigDecimal("0.750").compareTo(parsed.quantity()));
        assertTrue(parsed.hasQuantity());
    }

    @Test
    void twoStarsAreRefused_RN05() {
        assertThrows(InvalidQuantityException.class, () -> ScanInputParser.parse("2*3*123"));
    }

    @Test
    void emptyQuantityIsRefused_RN05() {
        assertThrows(InvalidQuantityException.class, () -> ScanInputParser.parse("*123"));
    }

    @Test
    void emptyCodeIsRefused_RN05() {
        assertThrows(InvalidQuantityException.class, () -> ScanInputParser.parse("5*"));
    }

    @Test
    void nonNumericQuantityIsRefused_RN05() {
        assertThrows(InvalidQuantityException.class, () -> ScanInputParser.parse("abc*123"));
    }

    @Test
    void nullInputBehavesAsEmptyCode_RN05() {
        assertEquals("", ScanInputParser.parse(null).code());
    }
}
