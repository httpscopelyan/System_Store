package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.service.PixService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste puro (sem Spring): buildStaticPayload não usa AppSettingService.
 */
class PixServiceTests {

    private final PixService pix = new PixService(null);

    @Test
    void payloadHasEmvStructure() {
        String payload = pix.buildStaticPayload(
                "chave@loja.com", "Mercado Scopel", "Sao Paulo", new BigDecimal("12.34"), "PEDIDO1");

        assertTrue(payload.startsWith("000201"), "payload format indicator");
        assertTrue(payload.contains("0014br.gov.bcb.pix"), "GUI do PIX");
        assertTrue(payload.contains("chave@loja.com"), "chave PIX presente");
        assertTrue(payload.contains("5303986"), "moeda BRL");
        assertTrue(payload.contains("5802BR"), "país BR");
        assertTrue(payload.contains("540512.34"), "valor (tlv 54, len 05)");
        // termina com 6304 + 4 hex
        assertTrue(payload.matches(".*6304[0-9A-F]{4}$"), "CRC final");
    }

    @Test
    void crcIsConsistent() {
        String payload = pix.buildStaticPayload(
                "11122233344", "Loja", "Cidade", null, null);

        String body = payload.substring(0, payload.length() - 4); // inclui "6304"
        String crc = payload.substring(payload.length() - 4);

        assertTrue(body.endsWith("6304"));
        assertEquals(expectedCrc(body), crc);
    }

    @Test
    void withoutAmountHasNoField54() {
        String payload = pix.buildStaticPayload("key", "Loja", "Cidade", null, "***");
        assertFalse(payload.contains("5303986" + "54"), "sem valor => sem campo 54");
    }

    // Reimplementação independente do CRC16/CCITT-FALSE para checagem cruzada.
    private String expectedCrc(String payload) {
        int crc = 0xFFFF;
        for (byte b : payload.getBytes(StandardCharsets.US_ASCII)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
