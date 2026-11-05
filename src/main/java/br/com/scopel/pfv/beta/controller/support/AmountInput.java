package br.com.scopel.pfv.beta.controller.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AmountInput {

    private static final int MAX_INTEGER_DIGITS = 6;
    private static final int MAX_DECIMAL_DIGITS = 2;

    private String integerPart = "";
    private String decimalPart = "";
    private boolean decimalMode;

    public void digit(char digit) {
        if (digit < '0' || digit > '9') {
            return;
        }
        if (decimalMode) {
            if (decimalPart.length() < MAX_DECIMAL_DIGITS) {
                decimalPart += digit;
            }
        } else if (integerPart.equals("0")) {
            if (digit != '0') {
                integerPart = String.valueOf(digit);
            }
        } else if (integerPart.length() < MAX_INTEGER_DIGITS) {
            integerPart += digit;
        }
    }

    public void comma() {
        decimalMode = true;
    }

    public void backspace() {
        if (decimalMode && !decimalPart.isEmpty()) {
            decimalPart = decimalPart.substring(0, decimalPart.length() - 1);
        } else if (decimalMode) {
            decimalMode = false;
        } else if (!integerPart.isEmpty()) {
            integerPart = integerPart.substring(0, integerPart.length() - 1);
        }
    }

    public void clear() {
        integerPart = "";
        decimalPart = "";
        decimalMode = false;
    }

    public String display() {
        String whole = integerPart.isEmpty() ? "0" : integerPart;
        return decimalMode ? whole + "," + decimalPart : whole;
    }

    public BigDecimal value() {
        String whole = integerPart.isEmpty() ? "0" : integerPart;
        String fraction = decimalPart.isEmpty() ? "0" : decimalPart;
        return new BigDecimal(whole + "." + fraction).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isPositive() {
        return value().signum() > 0;
    }
}
