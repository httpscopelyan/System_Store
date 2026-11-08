package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperatorLigiaSeedTests {

    @Test
    void seededHashMatchesTheAgreedPasswordAndNeverStoresItInPlainText() {
        assertTrue(new BCryptPasswordEncoder().matches("Mudar123!@", OperatorLigiaSeed.PASSWORD_HASH));
        assertTrue(OperatorLigiaSeed.PASSWORD_HASH.startsWith("$2a$"));
    }

    @Test
    void createsLigiaAsOperatorOnceAndMarksTheSeedDone() {
        UserService userService = mock(UserService.class);
        AppSettingService settings = mock(AppSettingService.class);
        when(userService.hasUsers()).thenReturn(true);
        when(settings.getBoolean(OperatorLigiaSeed.DONE_FLAG, false)).thenReturn(false);

        new OperatorLigiaSeed(userService, settings).run(null);

        verify(userService).registerWithPasswordHash("Ligia", "Ligia", OperatorLigiaSeed.PASSWORD_HASH,
                User.Role.OPERADOR);
        verify(settings).set(OperatorLigiaSeed.DONE_FLAG, "true");
    }

    @Test
    void doesNothingOnceDoneSoALaterPasswordChangeIsNeverOverwritten() {
        UserService userService = mock(UserService.class);
        AppSettingService settings = mock(AppSettingService.class);
        when(userService.hasUsers()).thenReturn(true);
        when(settings.getBoolean(OperatorLigiaSeed.DONE_FLAG, false)).thenReturn(true);

        new OperatorLigiaSeed(userService, settings).run(null);

        verify(userService, never()).registerWithPasswordHash(any(), any(), any(), any());
    }

    @Test
    void doesNotSeedOnAnEmptyDatabaseSoTheFirstUserSetupStillRuns() {
        UserService userService = mock(UserService.class);
        AppSettingService settings = mock(AppSettingService.class);
        when(userService.hasUsers()).thenReturn(false);

        new OperatorLigiaSeed(userService, settings).run(null);

        verify(userService, never()).registerWithPasswordHash(any(), any(), any(), any());
    }
}
