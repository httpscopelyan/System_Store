package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.PixNotConfiguredException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Gerador de "PIX Copia e Cola" estático (BR Code / padrão EMV do Banco Central),
 * equivalente ao "Gerador de QR Code PIX" do PDV Prático (tecla F12).
 *
 * É só montagem de string + CRC16; não fala com banco nem exige credencial.
 * A UI pode transformar o payload retornado em imagem de QR Code.
 */
@Service
public class PixService {

    public static final String PIX_KEY = "pix.key";
    public static final String PIX_MERCHANT_NAME = "pix.merchant_name";
    public static final String PIX_MERCHANT_CITY = "pix.merchant_city";

    private final AppSettingService appSettingService;

    public PixService(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
    }

    /** Monta o payload lendo chave/nome/cidade das configurações (ConfigADM_PIX). */
    public String buildFromSettings(BigDecimal amount, String txid) {
        String key = appSettingService.getString(PIX_KEY, null);
        if (key == null || key.isBlank()) {
            throw new PixNotConfiguredException();
        }
        String name = appSettingService.getString(PIX_MERCHANT_NAME, "LOJA");
        String city = appSettingService.getString(PIX_MERCHANT_CITY, "CIDADE");
        return buildStaticPayload(key, name, city, amount, txid);
    }

    /**
     * Monta o payload estático. amount nulo/zero gera QR sem valor definido;
     * txid nulo/vazio vira "***".
     */
    public String buildStaticPayload(String pixKey, String merchantName, String merchantCity,
                                     BigDecimal amount, String txid) {
        String name = sanitize(merchantName, 25);
        String city = sanitize(merchantCity, 15);
        String label = (txid == null || txid.isBlank()) ? "***" : sanitize(txid, 25);

        String merchantAccount = tlv("00", "br.gov.bcb.pix") + tlv("01", pixKey);

        StringBuilder sb = new StringBuilder();
        sb.append(tlv("00", "01"));          // payload format indicator
        sb.append(tlv("01", "11"));          // estático
        sb.append(tlv("26", merchantAccount));
        sb.append(tlv("52", "0000"));        // merchant category code
        sb.append(tlv("53", "986"));         // moeda BRL
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(tlv("54", amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        }
        sb.append(tlv("58", "BR"));          // país
        sb.append(tlv("59", name));
        sb.append(tlv("60", city));
        sb.append(tlv("62", tlv("05", label)));
        sb.append("6304");                   // id + len do CRC, calculado sobre tudo isso

        sb.append(crc16(sb.toString()));
        return sb.toString();
    }

    private String tlv(String id, String value) {
        return id + String.format("%02d", value.length()) + value;
    }

    private String sanitize(String text, int max) {
        if (text == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "")
                .trim();
        return noAccents.length() > max ? noAccents.substring(0, max) : noAccents;
    }

    // CRC16/CCITT-FALSE: poly 0x1021, init 0xFFFF, sem reflexão.
    private String crc16(String payload) {
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
