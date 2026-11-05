package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class DrawerShortcutTests {

    @Test
    void f12OpensTheDrawerAndNeverPrints() {
        PrinterPort printer = mock(PrinterPort.class);

        new DrawerShortcut(printer, message -> { }).openDrawer();

        verify(printer, timeout(2000)).openDrawer();
        verify(printer, never()).print(anyString());
    }
}
