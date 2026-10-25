package br.com.scopel.pfv.beta.hardware;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PrinterPort de fallback: manda para o log/console. Ativa quando
 * {@code pdv.printer.enabled} é false ou ausente (sem hardware). Com a
 * impressora ligada, quem assume é o {@link EscPosPrinter}.
 */
@Component
@ConditionalOnProperty(name = "pdv.printer.enabled", havingValue = "false", matchIfMissing = true)
public class ConsolePrinter implements PrinterPort {

    @Override
    public void print(String content) {
        System.out.println("---- IMPRESSAO ----");
        System.out.println(content);
        System.out.println("-------------------");
    }

    @Override
    public void openDrawer() {
        System.out.println("[gaveta] abrir");
    }
}
