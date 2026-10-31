package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.MissingReasonException;

public final class MovementReason {

    public static final int MIN_LENGTH = 3;

    private MovementReason() {
    }

    public static String require(String reason, int maxLength) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < MIN_LENGTH) {
            throw new MissingReasonException("Informe o motivo (mínimo de " + MIN_LENGTH + " caracteres).");
        }
        if (normalized.length() > maxLength) {
            throw new MissingReasonException("O motivo aceita no máximo " + maxLength + " caracteres.");
        }
        return normalized;
    }
}
