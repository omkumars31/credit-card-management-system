package com.example.ccbilling.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.Customer;
import com.example.ccbilling.domain.PaymentMethod;
import com.example.ccbilling.domain.PaymentMethodType;
import com.example.ccbilling.dto.PaymentDtos.PaymentMethodRequest;
import com.example.ccbilling.dto.PaymentDtos.PaymentMethodResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.CustomerRepository;
import com.example.ccbilling.repository.PaymentMethodRepository;
import com.example.ccbilling.support.TestData;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentMethodServiceTest {

    @Mock PaymentMethodRepository methods;
    @Mock CustomerRepository customers;
    @Mock AuditService audit;

    PaymentMethodService service;
    Customer customer;

    @BeforeEach
    void setUp() {
        service = new PaymentMethodService(methods, customers, audit);
        customer = TestData.customer(1);
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        when(methods.save(any(PaymentMethod.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void maskKeepsOnlyTheLastFourCharacters() {
        assertEquals("*******bank", PaymentMethodService.mask("ravi@okbank"));
        assertEquals("************1234", PaymentMethodService.mask("5555444433331234"));
        assertEquals("abc", PaymentMethodService.mask("abc"));
    }

    @Test
    void identifierIsStoredAndReturnedOnlyInMaskedForm() {
        PaymentMethodResponse r = service.add(1L, new PaymentMethodRequest(PaymentMethodType.UPI, " My UPI ", "ravi@okbank"));
        assertEquals("*******bank", r.maskedIdentifier());
        assertEquals("My UPI", r.displayName());
        assertFalse(r.toString().contains("ravi@okbank"));
        verify(audit).record(ArgumentMatchers.eq(AuditAction.PAYMENT_METHOD_ADDED), ArgumentMatchers.eq("PaymentMethod"),
                any(), ArgumentMatchers.contains("*******bank"));
    }

    @Test
    void deactivationIsScopedToTheOwner() {
        PaymentMethod m = TestData.method(5, customer);
        when(methods.findByIdAndCustomerId(5L, 1L)).thenReturn(Optional.of(m));

        assertFalse(service.deactivate(1L, 5L).active());
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> service.deactivate(2L, 5L)).getCode());
    }
}
