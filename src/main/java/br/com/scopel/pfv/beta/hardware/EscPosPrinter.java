package br.com.scopel.pfv.beta.hardware;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.output.PrinterOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.print.PrintService;
import java.io.IOException;

/**
 * Impressão física em impressora térmica ESC/POS (cupom) e abertura da gaveta
 * de dinheiro (pulso no conector RJ). Ativa com {@code pdv.printer.enabled=true}.
 *
 * Usa o serviço de impressão instalado no Windows pelo nome
 * ({@code pdv.printer.name}). A gaveta abre pelo pino {@code pdv.printer.drawer-pin}
 * (2 ou 5).
 */
@Component
@ConditionalOnProperty(name = "pdv.printer.enabled", havingValue = "true")
public class EscPosPrinter implements PrinterPort {

    private final String printerName;
    private final int drawerPin;

    public EscPosPrinter(@Value("${pdv.printer.name:}") String printerName,
                         @Value("${pdv.printer.drawer-pin:2}") int drawerPin) {
        this.printerName = printerName;
        this.drawerPin = drawerPin;
    }

    @Override
    public void print(String content) {
        try (EscPos escpos = open()) {
            for (String line : content.split("\n", -1)) {
                escpos.writeLF(line);
            }
            escpos.feed(4);
            escpos.cut(EscPos.CutMode.FULL);
        } catch (IOException e) {
            throw new PrinterException("Falha ao imprimir: " + e.getMessage(), e);
        }
    }

    @Override
    public void openDrawer() {
        try (EscPos escpos = open()) {
            EscPos.PinConnector pin = (drawerPin == 5)
                    ? EscPos.PinConnector.Pin_5
                    : EscPos.PinConnector.Pin_2;
            escpos.pulsePin(pin, 100, 200);
        } catch (IOException e) {
            throw new PrinterException("Falha ao abrir a gaveta: " + e.getMessage(), e);
        }
    }

    private EscPos open() throws IOException {
        if (printerName == null || printerName.isBlank()) {
            throw new PrinterException("Nome da impressora não configurado (pdv.printer.name).", null);
        }
        PrintService service = PrinterOutputStream.getPrintServiceByName(printerName);
        if (service == null) {
            throw new PrinterException("Impressora não encontrada no Windows: " + printerName, null);
        }
        return new EscPos(new PrinterOutputStream(service));
    }

    /** Erro de hardware de impressão; não deve derrubar a venda já gravada (RNF06). */
    public static class PrinterException extends RuntimeException {
        public PrinterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
