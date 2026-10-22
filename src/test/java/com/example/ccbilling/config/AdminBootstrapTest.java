package com.example.ccbilling.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ccbilling.domain.Administrator;
import com.example.ccbilling.repository.AdministratorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBootstrapTest {

    @Mock AdministratorRepository administrators;
    @Mock PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        when(encoder.encode(any())).thenReturn("{encoded}");
    }

    private AdminBootstrap bootstrap(String user, String pass, boolean generate) {
        return new AdminBootstrap(administrators, encoder, user, pass, "Admin", generate);
    }

    @Test
    void createsConfiguredAdministratorWithEncodedPassword() {
        bootstrap("root-admin", "a-very-long-passphrase", false).run(null);

        ArgumentCaptor<Administrator> saved = ArgumentCaptor.forClass(Administrator.class);
        verify(administrators).save(saved.capture());
        assertEquals("root-admin", saved.getValue().getUsername());
        assertEquals("{encoded}", saved.getValue().getPasswordHash());
        assertTrue(saved.getValue().isEnabled());
        verify(encoder).encode("a-very-long-passphrase");
    }

    @Test
    void doesNothingWithoutConfigurationWhenGenerationIsOff() {
        bootstrap("", "", false).run(null);
        verify(administrators, never()).save(any(Administrator.class));
    }

    @Test
    void skipsExistingAdministrator() {
        when(administrators.existsByUsername("root-admin")).thenReturn(true);
        bootstrap("root-admin", "a-very-long-passphrase", false).run(null);
        verify(administrators, never()).save(any(Administrator.class));
    }

    @Test
    void refusesShortPasswords() {
        assertThrows(IllegalStateException.class, () -> bootstrap("root-admin", "short", false).run(null));
        verify(administrators, never()).save(any(Administrator.class));
    }

    @Test
    void generatesRandomPasswordOnlyWhenAllowedAndNoAdministratorExists() {
        when(administrators.count()).thenReturn(0L);
        bootstrap("", "", true).run(null);
        ArgumentCaptor<String> pw = ArgumentCaptor.forClass(String.class);
        verify(encoder).encode(pw.capture());
        assertEquals(20, pw.getValue().length());
        verify(administrators).save(any(Administrator.class));
    }

    @Test
    void doesNotGenerateWhenAnAdministratorAlreadyExists() {
        when(administrators.count()).thenReturn(1L);
        bootstrap("", "", true).run(null);
        verify(administrators, never()).save(any(Administrator.class));
    }
}
