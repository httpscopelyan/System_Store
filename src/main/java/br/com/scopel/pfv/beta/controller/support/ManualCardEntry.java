package br.com.scopel.pfv.beta.controller.support;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class ManualCardEntry {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    private ManualCardEntry() {
    }

    public static String money(BigDecimal value) {
        return MONEY.format(value);
    }

    public static void show(VBox box, TextField receivedField, Label status, BigDecimal total) {
        box.setVisible(true);
        box.setManaged(true);
        status.setText("Digite R$ " + MONEY.format(total) + " na maquininha e cobre o cliente. "
                + "Depois informe o valor recebido e pressione ENTER.");
        Platform.runLater(receivedField::requestFocus);
    }

    public static String problemWith(String receivedText, BigDecimal total) {
        BigDecimal received = DecimalInput.parseOrNull(receivedText);
        if (received == null) {
            return "Informe o valor recebido na maquininha.";
        }
        if (received.compareTo(total) != 0) {
            return "O valor recebido (R$ " + MONEY.format(received)
                    + ") não confere com o total (R$ " + MONEY.format(total) + ").";
        }
        return null;
    }
}
