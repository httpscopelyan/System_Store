package br.com.scopel.pfv.beta.hardware.mercadopago;

import br.com.scopel.pfv.beta.exception.CardGatewayException;
import br.com.scopel.pfv.beta.service.AppSettingService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TerminalModeSyncTests {

    private final MercadoPagoTerminalModeControl control = mock(MercadoPagoTerminalModeControl.class);
    private final AppSettingService settings = mock(AppSettingService.class);
    private final TerminalModeSync sync = new TerminalModeSync(control, settings);

    @Test
    void standaloneTerminalMarksManualMode() {
        when(control.currentMode()).thenReturn(TerminalMode.STANDALONE);

        sync.sync();

        verify(settings).set(AppSettingService.TERMINAL_MANUAL_MODE, "true");
    }

    @Test
    void pdvTerminalClearsManualMode() {
        when(control.currentMode()).thenReturn(TerminalMode.PDV);

        sync.sync();

        verify(settings).set(AppSettingService.TERMINAL_MANUAL_MODE, "false");
    }

    @Test
    void unknownModeOrUnreachableApiKeepsTheCurrentSetting() {
        when(control.currentMode()).thenReturn(TerminalMode.UNKNOWN);
        sync.sync();
        when(control.currentMode()).thenThrow(new CardGatewayException("sem rede"));
        sync.sync();

        verify(settings, never()).set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
