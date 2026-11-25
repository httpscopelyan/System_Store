package br.com.scopel.pfv.beta.hardware;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.output.PrinterOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.print.PrintService;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

@Component
@ConditionalOnProperty(name = "pdv.printer.enabled", havingValue = "true")
public class EscPosPrinter implements PrinterPort {

    private static final long JOB_TIMEOUT_MILLIS = 6000;

    private final ReentrantLock printLock = new ReentrantLock();
    private final String printerName;
    private final int drawerPin;

    public EscPosPrinter(@Value("${pdv.printer.name:}") String printerName,
                         @Value("${pdv.printer.drawer-pin:2}") int drawerPin) {
        this.printerName = printerName;
        this.drawerPin = drawerPin;
        WindowsPrintSpool.warmUp(printerName);
    }

    @Override
    public void print(String content) {
        printLock.lock();
        try {
            prepare();
            try (EscPos escpos = open()) {
                for (String line : content.split("\n", -1)) {
                    escpos.writeLF(line);
                }
                escpos.feed(4);
                escpos.cut(EscPos.CutMode.FULL);
            } catch (IOException e) {
                throw new PrinterException("Falha ao imprimir: " + e.getMessage(), e);
            }
            WindowsPrintSpool.requireJobConsumed(printerName, JOB_TIMEOUT_MILLIS);
        } finally {
            printLock.unlock();
        }
    }

    @Override
    public void openDrawer() {
        prepare();
        if (Thread.interrupted()) {
            return;
        }
        try (EscPos escpos = open()) {
            EscPos.PinConnector pin = (drawerPin == 5)
                    ? EscPos.PinConnector.Pin_5
                    : EscPos.PinConnector.Pin_2;
            escpos.pulsePin(pin, 100, 200);
        } catch (IOException e) {
            throw new PrinterException("Falha ao abrir a gaveta: " + e.getMessage(), e);
        }
    }

    private void prepare() {
        WindowsPrintSpool.recoverAndRequireOnline(printerName);
        PrintService service = findService();
        PrinterIsAcceptingJobs accepting = service.getAttribute(PrinterIsAcceptingJobs.class);
        if (accepting == PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS) {
            throw new PrinterException("A impressora não está aceitando trabalhos (desligada ou offline).", null);
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

    public static class PrinterException extends RuntimeException {
        public PrinterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
