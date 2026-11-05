package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperatorSeedTests {

    @Test
    void seededHashesMatchTheAgreedPasswords() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches("ABC123", OperatorSeed.OPERATORS.get(0).passwordHash()));
        assertTrue(encoder.matches("abc123", OperatorSeed.OPERATORS.get(1).passwordHash()));
        assertEquals("Dayana", OperatorSeed.OPERATORS.get(0).login());
        assertEquals("Maria", OperatorSeed.OPERATORS.get(1).login());
    }

    @Test
    void existingInstallationGetsBothOperators() {
        UserService userService = mock(UserService.class);
        AppSettingService settings = mock(AppSettingService.class);
        when(userService.hasUsers()).thenReturn(true);

        new OperatorSeed(userService, settings).run(null);

        verify(userService).registerWithPasswordHash(eqLogin("Dayana"), anyString(), anyString(), any());
        verify(userService).registerWithPasswordHash(eqLogin("Maria"), anyString(), anyString(), any());
    }

    @Test
    void emptyDatabaseIsLeftToTheInitialBootstrap() {
        UserService userService = mock(UserService.class);
        AppSettingService settings = mock(AppSettingService.class);
        when(userService.hasUsers()).thenReturn(false);

        new OperatorSeed(userService, settings).run(null);

        verify(userService, never()).registerWithPasswordHash(anyString(), anyString(), anyString(), any(User.Role.class));
    }

    @Test
    void doesNotRecreateOperatorsAfterTheFirstRun() {
        UserService userService = mock(UserService.class);
        AppSettingService settings = mock(AppSettingService.class);
        when(userService.hasUsers()).thenReturn(true);
        when(settings.getBoolean(OperatorSeed.DONE_FLAG, false)).thenReturn(true);

        new OperatorSeed(userService, settings).run(null);

        verify(userService, never()).registerWithPasswordHash(anyString(), anyString(), anyString(), any(User.Role.class));
    }

    private static String eqLogin(String login) {
        return org.mockito.ArgumentMatchers.eq(login);
    }
}
