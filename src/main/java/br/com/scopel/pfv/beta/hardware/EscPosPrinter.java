package br.com.scopel.pfv.beta.hardware;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.output.PrinterOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.print.PrintService;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import javax.print.attribute.standard.QueuedJobCount;
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

    private static final int MAX_QUEUED_JOBS_FOR_DRAWER = 1;

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
        requireHealthyQueue();
        try (EscPos escpos = open()) {
            EscPos.PinConnector pin = (drawerPin == 5)
                    ? EscPos.PinConnector.Pin_5
                    : EscPos.PinConnector.Pin_2;
            escpos.pulsePin(pin, 100, 200);
        } catch (IOException e) {
            throw new PrinterException("Falha ao abrir a gaveta: " + e.getMessage(), e);
        }
    }

    private void requireHealthyQueue() {
        PrintService service = findService();
        PrinterIsAcceptingJobs accepting = service.getAttribute(PrinterIsAcceptingJobs.class);
        if (accepting != null && accepting == PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS) {
            throw new PrinterException("A impressora não está aceitando trabalhos (desligada ou offline).", null);
        }
        QueuedJobCount queued = service.getAttribute(QueuedJobCount.class);
        if (queued != null && queued.getValue() > MAX_QUEUED_JOBS_FOR_DRAWER) {
            throw new PrinterException("A fila da impressora está travada (" + queued.getValue()
                    + " trabalhos pendentes). Reinicie a impressora; a gaveta não foi acionada para evitar acionamentos atrasados.", null);
        }
    }

    private PrintService findService() {
        if (printerName == null || printerName.isBlank()) {
            throw new PrinterException("Nome da impressora não configurado (pdv.printer.name).", null);
        }
        PrintService service = PrinterOutputStream.getPrintServiceByName(printerName);
        if (service == null) {
            throw new PrinterException("Impressora não encontrada no Windows: " + printerName, null);
        }
        return service;
    }

    private EscPos open() throws IOException {
        PrintService service = findService();
        return new EscPos(new PrinterOutputStream(service));
    }

    /** Erro de hardware de impressão; não deve derrubar a venda já gravada (RNF06). */
    public static class PrinterException extends RuntimeException {
        public PrinterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
