package com.example.ccbilling.service;

import static com.example.ccbilling.support.TestData.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ccbilling.billing.BillingRules;
import com.example.ccbilling.domain.AllocationType;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.BillingEntry;
import com.example.ccbilling.domain.BinConfiguration;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.EntryType;
import com.example.ccbilling.domain.LateFeeLedgerEntry;
import com.example.ccbilling.domain.PaymentStatus;
import com.example.ccbilling.domain.Statement;
import com.example.ccbilling.dto.LedgerDtos.LateFeeResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.BillingEntryRepository;
import com.example.ccbilling.repository.LateFeeLedgerRepository;
import com.example.ccbilling.repository.PaymentAllocationRepository;
import com.example.ccbilling.repository.StatementRepository;
import com.example.ccbilling.support.MutableClock;
import com.example.ccbilling.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class LateFeeServiceTest {

    // statement 100: total 12000.00, minimum due 600.00, due date 2026-04-04, rules snapshot v1
    @Mock StatementRepository statements;
    @Mock LateFeeLedgerRepository ledger;
    @Mock BillingEntryRepository entries;
    @Mock PaymentAllocationRepository allocations;
    @Mock AuditService audit;

    BinConfiguration bin;
    CreditCard card;
    Statement statement;

    @BeforeEach
    void setUp() {
        bin = TestData.bin(1);
        card = TestData.card(10, TestData.customer(1), bin, LocalDate.of(2026, 1, 10));
        statement = TestData.statement(100, card, LocalDate.of(2026, 3, 15), "12000.00");
        when(statements.findByIdForUpdate(100L)).thenReturn(Optional.of(statement));
        when(entries.save(any(BillingEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledger.save(any(LateFeeLedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        paidOnTime("300.00");
    }

    private void paidOnTime(String amount) {
        when(allocations.sumValidAllocationsBefore(eq(100L), eq(AllocationType.ALLOCATION), eq(PaymentStatus.COMPLETED),
                any(LocalDateTime.class))).thenReturn(amount == null ? null : bd(amount));
    }

    private LateFeeService serviceAt(LocalDate today) {
        return new LateFeeService(statements, ledger, entries, allocations, audit, MutableClock.at(today));
    }

    @Test
    void assessesFeeWithGstWritesLedgerAndPostsBillingEntry() {
        LateFeeResponse r = serviceAt(LocalDate.of(2026, 4, 10)).assess(100L);

        // unpaid minimum = 600 - 300 = 300; 10% = 30 -> floor 100; GST 18% = 18
        assertEquals(bd("300.00"), r.unpaidMinimumDue());
        assertEquals(bd("100.00"), r.feeAmount());
        assertEquals(bd("18.00"), r.gstAmount());
        assertEquals(bd("118.00"), r.totalAmount());
        assertEquals("LATE_PAYMENT", r.feeEvent());
        assertEquals("POSTED", r.status());
        assertTrue(r.ruleSnapshot().contains("ruleVersion=1") && r.ruleSnapshot().contains("lateFeeFloor=100"));

        ArgumentCaptor<BillingEntry> entry = ArgumentCaptor.forClass(BillingEntry.class);
        verify(entries).save(entry.capture());
        assertEquals(EntryType.LATE_FEE, entry.getValue().getEntryType());
        assertEquals(bd("118.00"), entry.getValue().getAmount());
        assertEquals("LATEFEE-100", entry.getValue().getReferenceId());
        verify(audit).record(eq(AuditAction.LATE_FEE_ASSESSED), eq("LateFeeLedger"), any(), any());
    }

    @Test
    void secondAssessmentForTheSameStatementIsRejected() {
        when(ledger.existsByStatementIdAndFeeEvent(100L, "LATE_PAYMENT")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> serviceAt(LocalDate.of(2026, 4, 10)).assess(100L));

        assertEquals("LATE_FEE_ALREADY_ASSESSED", ex.getCode());
        verify(ledger, never()).save(any(LateFeeLedgerEntry.class));
        verify(entries, never()).save(any(BillingEntry.class));
    }

    @Test
    void dueDateItselfIsNotOverdueButTheNextDayIs() {
        ApiException ex = assertThrows(ApiException.class, () -> serviceAt(LocalDate.of(2026, 4, 4)).assess(100L));
        assertEquals("NOT_OVERDUE", ex.getCode());

        LateFeeResponse r = serviceAt(LocalDate.of(2026, 4, 5)).assess(100L);
        assertEquals(bd("100.00"), r.feeAmount());
    }

    @Test
    void noFeeWhenMinimumDueWasPaidOnTime() {
        paidOnTime("600.00");
        ApiException ex = assertThrows(ApiException.class, () -> serviceAt(LocalDate.of(2026, 4, 10)).assess(100L));
        assertEquals("MINIMUM_DUE_SATISFIED", ex.getCode());

        paidOnTime("650.00");
        assertEquals("MINIMUM_DUE_SATISFIED",
                assertThrows(ApiException.class, () -> serviceAt(LocalDate.of(2026, 4, 10)).assess(100L)).getCode());
    }

    @Test
    void nothingPaidOnTimeMeansTheWholeMinimumIsUnpaid() {
        paidOnTime(null);
        LateFeeResponse r = serviceAt(LocalDate.of(2026, 4, 10)).assess(100L);
        // 10% of 600 = 60 -> floor 100
        assertEquals(bd("600.00"), r.unpaidMinimumDue());
        assertEquals(bd("100.00"), r.feeAmount());
    }

    @Test
    void feeFollowsPercentageAndCapForLargeUnpaidAmounts() {
        Statement big = TestData.statement(101, card, LocalDate.of(2026, 3, 15), "200000.00"); // min due 10000.00
        when(statements.findByIdForUpdate(101L)).thenReturn(Optional.of(big));
        when(allocations.sumValidAllocationsBefore(eq(101L), any(), any(), any())).thenReturn(null);

        LateFeeResponse r = serviceAt(LocalDate.of(2026, 4, 10)).assess(101L);
        assertEquals(bd("1000.00"), r.feeAmount()); // 10% of 10000 = 1000 = cap
        assertEquals(bd("180.00"), r.gstAmount());
    }

    @Test
    void noFeeBelowTheStatementThreshold() {
        Statement small = TestData.statement(102, card, LocalDate.of(2026, 3, 15), "400.00"); // threshold 500
        when(statements.findByIdForUpdate(102L)).thenReturn(Optional.of(small));
        when(allocations.sumValidAllocationsBefore(eq(102L), any(), any(), any())).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class, () -> serviceAt(LocalDate.of(2026, 4, 10)).assess(102L));
        assertEquals("NO_LATE_FEE_APPLICABLE", ex.getCode());
        verify(ledger, never()).save(any(LateFeeLedgerEntry.class));
    }

    @Test
    void usesTheRulesSnapshottedOnTheStatementNotTheCurrentBinRules() {
        bin.update("Test BIN", new BillingRules(15, 20, bd("5"), bd("200"), bd("3.5"), bd("28"), bd("10"),
                bd("500"), bd("2000"), bd("500")));

        LateFeeResponse r = serviceAt(LocalDate.of(2026, 4, 10)).assess(100L);

        assertEquals(bd("100.00"), r.feeAmount()); // old floor, not the new 500
        assertEquals(bd("18.00"), r.gstAmount());  // old GST 18%, not 28%
        assertEquals(1, r.ruleVersion());
    }

    @Test
    void unknownStatementIsNotFound() {
        assertEquals("NOT_FOUND",
                assertThrows(ApiException.class, () -> serviceAt(LocalDate.of(2026, 4, 10)).assess(999L)).getCode());
    }

    @Test
    void feeAmountsKeepScaleTwo() {
        LateFeeResponse r = serviceAt(LocalDate.of(2026, 4, 10)).assess(100L);
        for (BigDecimal v : new BigDecimal[]{r.feeAmount(), r.gstAmount(), r.totalAmount()}) {
            assertEquals(2, v.scale());
        }
    }
}
