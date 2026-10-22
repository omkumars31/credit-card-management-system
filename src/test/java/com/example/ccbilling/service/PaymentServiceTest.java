package com.example.ccbilling.service;

import static com.example.ccbilling.support.TestData.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ccbilling.billing.CardNumbers;
import com.example.ccbilling.domain.AllocationType;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.BinConfiguration;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.Customer;
import com.example.ccbilling.domain.Payment;
import com.example.ccbilling.domain.PaymentAllocation;
import com.example.ccbilling.domain.PaymentMethod;
import com.example.ccbilling.domain.PaymentStatus;
import com.example.ccbilling.domain.Statement;
import com.example.ccbilling.domain.StatementPaymentStatus;
import com.example.ccbilling.dto.PaymentDtos.PaymentRequest;
import com.example.ccbilling.dto.PaymentDtos.PaymentResponse;
import com.example.ccbilling.dto.PaymentDtos.PaymentResult;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.PaymentAllocationRepository;
import com.example.ccbilling.repository.PaymentMethodRepository;
import com.example.ccbilling.repository.PaymentRepository;
import com.example.ccbilling.repository.StatementRepository;
import com.example.ccbilling.support.MutableClock;
import com.example.ccbilling.support.TestData;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    private static final String KEY = "pay-key-0001";

    @Mock PaymentRepository payments;
    @Mock PaymentAllocationRepository allocations;
    @Mock PaymentMethodRepository methods;
    @Mock StatementRepository statements;
    @Mock AuditService audit;

    PaymentService service;
    Customer owner;
    CreditCard card;
    Statement statement;
    PaymentMethod method;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, allocations, methods, statements, audit,
                MutableClock.at(LocalDate.of(2026, 3, 20)));
        owner = TestData.customer(1);
        BinConfiguration bin = TestData.bin(1);
        card = TestData.card(10, owner, bin, LocalDate.of(2026, 1, 10));
        statement = TestData.statement(100, card, LocalDate.of(2026, 3, 15), "12000.00"); // min due 600.00
        method = TestData.method(5, owner);

        when(statements.findByIdForUpdate(100L)).thenReturn(Optional.of(statement));
        when(statements.findTopByCardIdOrderByPeriodEndDesc(10L)).thenReturn(Optional.of(statement));
        when(methods.findByIdAndCustomerId(5L, 1L)).thenReturn(Optional.of(method));
        when(payments.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private PaymentRequest request(String amount) {
        return new PaymentRequest(100L, 5L, bd(amount));
    }

    @Test
    void partialPaymentReducesOutstandingMarksPartiallyPaidAndWritesAllocationAndAudit() {
        PaymentResult result = service.pay(1L, KEY, request("300.00"));

        assertFalse(result.replayed());
        assertEquals(PaymentStatus.COMPLETED, result.payment().status());
        assertEquals(bd("300.00"), statement.getPaidAmount());
        assertEquals(bd("11700.00"), statement.outstanding());
        assertEquals(StatementPaymentStatus.PARTIALLY_PAID, statement.getPaymentStatus());

        ArgumentCaptor<PaymentAllocation> captor = ArgumentCaptor.forClass(PaymentAllocation.class);
        verify(allocations).save(captor.capture());
        assertEquals(AllocationType.ALLOCATION, captor.getValue().getAllocationType());
        assertEquals(bd("300.00"), captor.getValue().getAmount());
        verify(audit).record(eq(AuditAction.PAYMENT_COMPLETED), eq("Payment"), any(), contains("amount=300.00"));
    }

    @Test
    void fullPaymentMarksStatementPaid() {
        service.pay(1L, KEY, request("12000.00"));

        assertEquals(StatementPaymentStatus.PAID, statement.getPaymentStatus());
        assertEquals(bd("0.00"), statement.outstanding());
    }

    @Test
    void twoPartialPaymentsAccumulate() {
        service.pay(1L, "pay-key-0001", request("600.00"));
        service.pay(1L, "pay-key-0002", request("400.00"));

        assertEquals(bd("1000.00"), statement.getPaidAmount());
        assertEquals(StatementPaymentStatus.PARTIALLY_PAID, statement.getPaymentStatus());
    }

    @Test
    void replayWithSameKeyAndPayloadReturnsOriginalWithoutNewMoneyMovement() {
        String fingerprint = CardNumbers.sha256("100|5|300.00");
        Payment original = new Payment(owner, statement, method, KEY, fingerprint, bd("300.00"), TestData.NOW);
        TestData.setId(original, 77);
        statement.applyPayment(bd("300.00"));
        when(payments.findByCustomerIdAndIdempotencyKey(1L, KEY)).thenReturn(Optional.of(original));

        PaymentResult result = service.pay(1L, KEY, request("300.00"));

        assertTrue(result.replayed());
        assertEquals(77L, result.payment().id());
        assertEquals(bd("300.00"), statement.getPaidAmount()); // not applied twice
        verify(payments, never()).save(any(Payment.class));
        verify(allocations, never()).save(any(PaymentAllocation.class));
    }

    @Test
    void reusingAKeyWithDifferentPayloadIsRejected() {
        Payment original = new Payment(owner, statement, method, KEY, CardNumbers.sha256("100|5|300.00"),
                bd("300.00"), TestData.NOW);
        when(payments.findByCustomerIdAndIdempotencyKey(1L, KEY)).thenReturn(Optional.of(original));

        ApiException ex = assertThrows(ApiException.class, () -> service.pay(1L, KEY, request("301.00")));

        assertEquals("IDEMPOTENCY_KEY_REUSED", ex.getCode());
        verify(payments, never()).save(any(Payment.class));
    }

    @Test
    void anotherCustomersStatementLooksLikeNotFound() {
        Customer other = TestData.customer(2);
        ApiException ex = assertThrows(ApiException.class, () -> service.pay(other.getId(), KEY, request("100.00")));

        assertEquals("NOT_FOUND", ex.getCode());
        assertEquals(bd("0.00"), statement.getPaidAmount());
    }

    @Test
    void inactivePaymentMethodIsRejected() {
        method.deactivate();
        ApiException ex = assertThrows(ApiException.class, () -> service.pay(1L, KEY, request("100.00")));
        assertEquals("PAYMENT_METHOD_INACTIVE", ex.getCode());
    }

    @Test
    void unknownOrForeignPaymentMethodIsNotFound() {
        when(methods.findByIdAndCustomerId(5L, 1L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.pay(1L, KEY, request("100.00")));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void paymentAboveOutstandingIsRejectedAndNothingIsRecorded() {
        ApiException ex = assertThrows(ApiException.class, () -> service.pay(1L, KEY, request("12000.01")));

        assertEquals("PAYMENT_EXCEEDS_OUTSTANDING", ex.getCode());
        verify(payments, never()).save(any(Payment.class));
        verify(audit, never()).record(any(), any(), any(), any());
        assertEquals(bd("0.00"), statement.getPaidAmount());
    }

    @Test
    void paymentExactlyEqualToOutstandingIsAccepted() {
        service.pay(1L, "pay-key-0001", request("11999.99"));
        service.pay(1L, "pay-key-0002", request("0.01"));
        assertEquals(StatementPaymentStatus.PAID, statement.getPaymentStatus());
    }

    @Test
    void supersededStatementCannotBePaid() {
        Statement newer = TestData.statement(101, card, LocalDate.of(2026, 4, 15), "500.00");
        when(statements.findTopByCardIdOrderByPeriodEndDesc(10L)).thenReturn(Optional.of(newer));

        ApiException ex = assertThrows(ApiException.class, () -> service.pay(1L, KEY, request("100.00")));
        assertEquals("STATEMENT_SUPERSEDED", ex.getCode());
    }

    @Test
    void settledStatementHasNothingDue() {
        statement.applyPayment(bd("12000.00"));
        ApiException ex = assertThrows(ApiException.class, () -> service.pay(1L, KEY, request("1.00")));
        assertEquals("NOTHING_DUE", ex.getCode());
    }

    @Test
    void idempotencyKeyIsMandatoryAndValidated() {
        assertEquals("INVALID_IDEMPOTENCY_KEY",
                assertThrows(ApiException.class, () -> service.pay(1L, null, request("1.00"))).getCode());
        assertEquals("INVALID_IDEMPOTENCY_KEY",
                assertThrows(ApiException.class, () -> service.pay(1L, "short", request("1.00"))).getCode());
        assertEquals("INVALID_IDEMPOTENCY_KEY",
                assertThrows(ApiException.class, () -> service.pay(1L, "has spaces in it!", request("1.00"))).getCode());
    }

    @Test
    void amountWithMoreThanTwoDecimalsOrNonPositiveIsRejected() {
        assertEquals("INVALID_AMOUNT",
                assertThrows(ApiException.class, () -> service.pay(1L, KEY, request("10.005"))).getCode());
        assertEquals("INVALID_AMOUNT",
                assertThrows(ApiException.class, () -> service.pay(1L, KEY, request("0.00"))).getCode());
        assertEquals("INVALID_AMOUNT",
                assertThrows(ApiException.class, () -> service.pay(1L, KEY, request("-5.00"))).getCode());
    }

    // ---- reversal ----

    private Payment completedPayment(String amount) {
        Payment p = new Payment(owner, statement, method, KEY, "fp", bd(amount), TestData.NOW);
        TestData.setId(p, 7);
        statement.applyPayment(bd(amount));
        when(payments.findStatementIdById(7L)).thenReturn(Optional.of(100L));
        when(payments.findByIdForUpdate(7L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void reversalRestoresStatementAppendsNegativeAllocationAndAudits() {
        Payment p = completedPayment("300.00");

        PaymentResponse response = service.reverse(7L, "  bounced  ");

        assertEquals(PaymentStatus.REVERSED, response.status());
        assertEquals("bounced", p.getReversalReason());
        assertEquals(bd("0.00"), statement.getPaidAmount());
        assertEquals(StatementPaymentStatus.UNPAID, statement.getPaymentStatus());
        ArgumentCaptor<PaymentAllocation> captor = ArgumentCaptor.forClass(PaymentAllocation.class);
        verify(allocations).save(captor.capture());
        assertEquals(AllocationType.REVERSAL, captor.getValue().getAllocationType());
        assertEquals(bd("-300.00"), captor.getValue().getAmount());
        verify(audit).record(eq(AuditAction.PAYMENT_REVERSED), eq("Payment"), eq(7L), contains("amount=300.00"));
    }

    @Test
    void aPaymentCanOnlyBeReversedOnce() {
        completedPayment("300.00");
        service.reverse(7L, "first");

        ApiException ex = assertThrows(ApiException.class, () -> service.reverse(7L, "second"));
        assertEquals("PAYMENT_ALREADY_REVERSED", ex.getCode());
        assertEquals(bd("0.00"), statement.getPaidAmount()); // not reversed twice
    }

    @Test
    void reversalIsRefusedOnceANewerStatementExists() {
        completedPayment("300.00");
        Statement newer = TestData.statement(101, card, LocalDate.of(2026, 4, 15), "500.00");
        when(statements.findTopByCardIdOrderByPeriodEndDesc(10L)).thenReturn(Optional.of(newer));

        ApiException ex = assertThrows(ApiException.class, () -> service.reverse(7L, "late"));
        assertEquals("REVERSAL_NOT_ALLOWED", ex.getCode());
        assertEquals(bd("300.00"), statement.getPaidAmount());
    }

    @Test
    void reversingUnknownPaymentIsNotFound() {
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> service.reverse(999L, "x")).getCode());
    }
}
