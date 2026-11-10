package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdministratorYanSeedTests {

    private static final String BCRYPT_COST_TEN_FORMAT = "^\\$2a\\$10\\$[./A-Za-z0-9]{53}$";

    @Test
    void seededHashIsAValidBcryptHashAndNeverMatchesAWrongPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        assertTrue(AdministratorYanSeed.PASSWORD_HASH.matches(BCRYPT_COST_TEN_FORMAT));
        assertEquals(60, AdministratorYanSeed.PASSWORD_HASH.length());
        assertFalse(encoder.upgradeEncoding(AdministratorYanSeed.PASSWORD_HASH));
        assertFalse(encoder.matches("senha-errada", AdministratorYanSeed.PASSWORD_HASH));
        assertFalse(encoder.matches("", AdministratorYanSeed.PASSWORD_HASH));
        assertFalse(encoder.matches(AdministratorYanSeed.LOGIN, AdministratorYanSeed.PASSWORD_HASH));
    }

    @Test
    void createsYanAsManagerOnceAndMarksTheSeedDone() {
        UserService userService = mock(UserService.class);
        AppSettingService settings = mock(AppSettingService.class);
        when(userService.hasUsers()).thenReturn(true);
        when(settings.getBoolean(AdministratorYanSeed.DONE_FLAG, false)).thenReturn(false);
        when(userService.registerWithPasswordHash(any(), any(), any(), any())).thenReturn(true);

        new AdministratorYanSeed(userService, settings).run(null);

        verify(userService).registerWithPasswordHash("Yan", "Yan", AdministratorYanSeed.PASSWORD_HASH,
                User.Role.GERENTE);
        verify(settings).set(AdministratorYanSeed.DONE_FLAG, "true");
        assertEquals("seed.administrator_yan.done", AdministratorYanSeed.DONE_FLAG);
    }

    @Test
    void existingYanAccountIsLeftUntouchedAndTheSeedIsStillMarkedDone() {
        UserService userService = mock(UserService.class);
        AppSettingService settings = mock(AppSettingService.class);
        when(userService.hasUsers()).thenReturn(true);
        when(settings.getBoolean(AdministratorYanSeed.DONE_FLAG, false)).thenReturn(false);
        when(userService.registerWithPasswordHash(any(), any(), any(), any())).thenReturn(false);

        new AdministratorYanSeed(userService, settings).run(null);

        verify(settings).set(AdministratorYanSeed.DONE_FLAG, "true");
    }

    @Test
    void doesNothingOnceDoneSoALaterPasswordChangeIsNeverOverwritten() {
        UserService userService = mock(UserService.class);
        AppSettingService settings = mock(AppSettingService.class);
        when(userService.hasUsers()).thenReturn(true);
        when(settings.getBoolean(AdministratorYanSeed.DONE_FLAG, false)).thenReturn(true);

        new AdministratorYanSeed(userService, settings).run(null);

        verify(userService, never()).registerWithPasswordHash(any(), any(), any(), any());
    }

    @Test
    void doesNotSeedOnAnEmptyDatabaseSoTheFirstUserSetupStillRuns() {
        UserService userService = mock(UserService.class);
        AppSettingService settings = mock(AppSettingService.class);
        when(userService.hasUsers()).thenReturn(false);

        new AdministratorYanSeed(userService, settings).run(null);

        verify(userService, never()).registerWithPasswordHash(any(), any(), any(), any());
        verify(settings, never()).set(any(), any());
    }
}
