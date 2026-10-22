package com.example.ccbilling.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ccbilling.domain.Administrator;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.Customer;
import com.example.ccbilling.dto.AdminDtos.CreateAdminRequest;
import com.example.ccbilling.dto.AuthDtos.RegisterRequest;
import com.example.ccbilling.dto.CustomerDtos.CustomerResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.AdministratorRepository;
import com.example.ccbilling.repository.CustomerRepository;
import com.example.ccbilling.support.TestData;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerAndAdminServiceTest {

    @Mock CustomerRepository customers;
    @Mock AdministratorRepository administrators;
    @Mock PasswordEncoder encoder;
    @Mock AuditService audit;

    CustomerService customerService;
    AdminAccountService adminService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customers, encoder, audit);
        adminService = new AdminAccountService(administrators, encoder, audit);
        when(encoder.encode(any())).thenReturn("{encoded}hash");
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(administrators.save(any(Administrator.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registrationNormalisesEmailHashesPasswordAndNeverReturnsIt() {
        CustomerResponse r = customerService.register(new RegisterRequest("  Asha Rao ", "  Asha@Example.COM ", "", "S3cretPassw0rd"));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(saved.capture());
        assertEquals("asha@example.com", saved.getValue().getEmail());
        assertEquals("Asha Rao", saved.getValue().getFullName());
        assertEquals("{encoded}hash", saved.getValue().getPasswordHash());
        assertEquals(null, saved.getValue().getPhone());
        assertEquals("asha@example.com", r.email());
        assertTrue(!r.toString().contains("S3cretPassw0rd") && !r.toString().contains("encoded"));
        verify(audit).recordAs(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq("asha@example.com"),
                ArgumentMatchers.eq(AuditAction.CUSTOMER_REGISTERED), ArgumentMatchers.eq("Customer"), ArgumentMatchers.any(),
                ArgumentMatchers.any());
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() {
        when(customers.existsByEmail("asha@example.com")).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class,
                () -> customerService.register(new RegisterRequest("Asha", "ASHA@example.com", null, "S3cretPassw0rd")));
        assertEquals("EMAIL_ALREADY_REGISTERED", ex.getCode());
        verify(customers, never()).save(any(Customer.class));
    }

    @Test
    void profileOfUnknownCustomerIsNotFound() {
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> customerService.getProfile(5L)).getCode());
    }

    @Test
    void adminCreationHashesPasswordAndRejectsDuplicates() {
        var r = adminService.create(new CreateAdminRequest("ops.admin", "Ops Admin", "a-long-password-1"));
        assertEquals("ops.admin", r.username());
        assertTrue(r.enabled());

        when(administrators.existsByUsername("ops.admin")).thenReturn(true);
        assertEquals("USERNAME_TAKEN", assertThrows(ApiException.class,
                () -> adminService.create(new CreateAdminRequest("ops.admin", "x", "a-long-password-1"))).getCode());
    }

    @Test
    void administratorCannotDisableOwnAccountButCanDisableOthers() {
        Administrator me = new Administrator("me", "Me", "{h}");
        TestData.setId(me, 1);
        Administrator other = new Administrator("other", "Other", "{h}");
        TestData.setId(other, 2);
        when(administrators.findById(1L)).thenReturn(Optional.of(me));
        when(administrators.findById(2L)).thenReturn(Optional.of(other));

        assertEquals("CANNOT_DISABLE_SELF",
                assertThrows(ApiException.class, () -> adminService.setEnabled(1L, false, 1L)).getCode());
        assertTrue(me.isEnabled());

        assertEquals(false, adminService.setEnabled(2L, false, 1L).enabled());
    }
}
