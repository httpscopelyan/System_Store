package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.exception.PixNotConfiguredException;
import br.com.scopel.pfv.beta.service.PixService;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.math.BigDecimal;

public final class SplitManualCharge {

    public record Outcome(boolean readyToRegister, String message) {
    }

    private final PixService pixService;
    private final TextField machineReceivedField;
    private final TextArea pixArea;

    private boolean pixShown;

    public SplitManualCharge(PixService pixService, TextField machineReceivedField, TextArea pixArea) {
        this.pixService = pixService;
        this.machineReceivedField = machineReceivedField;
        this.pixArea = pixArea;
    }

    public void reset() {
        pixShown = false;
        machineReceivedField.clear();
    }

    public boolean isPixShown() {
        return pixShown;
    }

    public Outcome confirm(SplitPartMethod method, BigDecimal amount, Long saleId) {
        return method == SplitPartMethod.PIX ? confirmPix(amount, saleId) : confirmCard(amount);
    }

    private Outcome confirmCard(BigDecimal amount) {
        BigDecimal received = DecimalInput.parseOrNull(machineReceivedField.getText());
        if (received == null || received.compareTo(amount) != 0) {
            return new Outcome(false, "Digite " + MoneyFormat.format(amount)
                    + " na maquininha e informe o valor recebido, igual ao desta parte.");
        }
        return new Outcome(true, "");
    }

    private Outcome confirmPix(BigDecimal amount, Long saleId) {
        if (pixShown) {
            return new Outcome(true, "");
        }
        try {
            pixArea.setText(pixService.buildFromSettings(amount, "VENDA" + saleId));
            pixShown = true;
            return new Outcome(false, "Copie o código e pressione Enter ao receber o PIX.");
        } catch (PixNotConfiguredException notConfigured) {
            return new Outcome(false, "Configure a chave PIX em Configurações");
        }
    }
}
