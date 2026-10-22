package com.example.ccbilling.service;

import static com.example.ccbilling.support.TestData.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ccbilling.billing.BillingRules;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.BillingEntry;
import com.example.ccbilling.domain.BinConfiguration;
import com.example.ccbilling.domain.CardStatus;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.Customer;
import com.example.ccbilling.domain.EntryType;
import com.example.ccbilling.domain.Statement;
import com.example.ccbilling.domain.StatementPaymentStatus;
import com.example.ccbilling.dto.StatementDtos.StatementResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.BillingEntryRepository;
import com.example.ccbilling.repository.CreditCardRepository;
import com.example.ccbilling.repository.StatementRepository;
import com.example.ccbilling.support.MutableClock;
import com.example.ccbilling.support.TestData;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatementServiceTest {

    @Mock CreditCardRepository cards;
    @Mock StatementRepository statements;
    @Mock BillingEntryRepository entries;
    @Mock AuditService audit;

    BinConfiguration bin;
    CreditCard card;

    @BeforeEach
    void setUp() {
        Customer customer = TestData.customer(1);
        bin = TestData.bin(1);
        card = TestData.card(10, customer, bin, LocalDate.of(2026, 1, 10));
        when(cards.findByIdForUpdate(10L)).thenReturn(Optional.of(card));
        when(statements.save(any(Statement.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private StatementService serviceAt(LocalDate today) {
        return new StatementService(cards, statements, entries, audit, MutableClock.at(today));
    }

    private List<BillingEntry> firstCycleEntries() {
        return List.of(
                TestData.entry(card, "p1", EntryType.PURCHASE, "10000.00", LocalDate.of(2026, 2, 20)),
                TestData.entry(card, "p2", EntryType.PURCHASE, "2500.50", LocalDate.of(2026, 3, 1)),
                TestData.entry(card, "r1", EntryType.REFUND, "500.50", LocalDate.of(2026, 3, 5)));
    }

    @Test
    void firstStatementTotalsEntriesAndComputesDueDateAndMinimumDue() {
        List<BillingEntry> unbilled = firstCycleEntries();
        when(entries.findUnbilledUpTo(10L, LocalDate.of(2026, 3, 15))).thenReturn(unbilled);

        StatementResponse r = serviceAt(LocalDate.of(2026, 3, 20)).generate(10L, LocalDate.of(2026, 3, 15));

        assertEquals(LocalDate.of(2026, 2, 16), r.periodStart());
        assertEquals(LocalDate.of(2026, 3, 15), r.periodEnd());
        assertEquals(LocalDate.of(2026, 4, 4), r.dueDate());
        assertEquals(bd("0.00"), r.previousBalance());
        assertEquals(bd("12500.50"), r.purchasesTotal());
        assertEquals(bd("500.50"), r.creditsTotal());
        assertEquals(bd("0.00"), r.financeCharge());
        assertEquals(bd("12000.00"), r.totalAmountDue());
        assertEquals(bd("600.00"), r.minimumAmountDue());
        assertEquals(bd("0.00"), r.paidAmount());
        assertEquals(StatementPaymentStatus.UNPAID, r.paymentStatus());
        unbilled.forEach(e -> assertNotNull(e.getStatement(), "entry must be marked as billed"));
        verify(audit).record(eq(AuditAction.STATEMENT_GENERATED), eq("Statement"), any(), contains("total=12000.00"));
    }

    @Test
    void secondStatementCarriesUnpaidBalanceAddsFinanceChargeGstAndLateFee() {
        Statement first = TestData.statement(100, card, LocalDate.of(2026, 3, 15), "12000.00");
        first.applyPayment(bd("300.00")); // outstanding 11700.00
        when(statements.findTopByCardIdOrderByPeriodEndDesc(10L)).thenReturn(Optional.of(first));
        List<BillingEntry> unbilled = List.of(
                TestData.entry(card, "p4", EntryType.PURCHASE, "1000.00", LocalDate.of(2026, 4, 10)),
                TestData.entry(card, "LATEFEE-100", EntryType.LATE_FEE, "118.00", LocalDate.of(2026, 4, 10)));
        when(entries.findUnbilledUpTo(10L, LocalDate.of(2026, 4, 15))).thenReturn(unbilled);

        StatementResponse r = serviceAt(LocalDate.of(2026, 4, 20)).generate(10L, LocalDate.of(2026, 4, 15));

        assertEquals(LocalDate.of(2026, 3, 16), r.periodStart());
        assertEquals(LocalDate.of(2026, 5, 5), r.dueDate());
        assertEquals(bd("11700.00"), r.previousBalance());
        assertEquals(bd("1000.00"), r.purchasesTotal());
        assertEquals(bd("118.00"), r.feesTotal());
        assertEquals(bd("409.50"), r.financeCharge());
        assertEquals(bd("73.71"), r.gstOnFinanceCharge());
        assertEquals(bd("13301.21"), r.totalAmountDue());
        assertEquals(bd("665.06"), r.minimumAmountDue());
    }

    @Test
    void duplicateStatementForSameCardAndPeriodIsRejectedWithoutCharges() {
        when(statements.existsByCardIdAndPeriodEnd(10L, LocalDate.of(2026, 3, 15))).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> serviceAt(LocalDate.of(2026, 3, 20)).generate(10L, LocalDate.of(2026, 3, 15)));

        assertEquals("STATEMENT_ALREADY_EXISTS", ex.getCode());
        verify(statements, never()).save(any(Statement.class));
        verify(entries, never()).findUnbilledUpTo(any(), any());
    }

    @Test
    void periodThatHasNotEndedYetIsRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> serviceAt(LocalDate.of(2026, 3, 20)).generate(10L, LocalDate.of(2026, 4, 15)));
        assertEquals("PERIOD_NOT_ENDED", ex.getCode());
    }

    @Test
    void periodEndOnTheCycleDayItselfIsAllowed() {
        when(entries.findUnbilledUpTo(10L, LocalDate.of(2026, 3, 15))).thenReturn(List.of());
        StatementResponse r = serviceAt(LocalDate.of(2026, 3, 15)).generate(10L, LocalDate.of(2026, 3, 15));
        assertEquals(bd("0.00"), r.totalAmountDue());
        assertEquals(StatementPaymentStatus.PAID, r.paymentStatus()); // nothing to pay
    }

    @Test
    void periodEndMustFallOnTheBinCycleDay() {
        ApiException ex = assertThrows(ApiException.class,
                () -> serviceAt(LocalDate.of(2026, 3, 20)).generate(10L, LocalDate.of(2026, 3, 14)));
        assertEquals("INVALID_PERIOD_END", ex.getCode());
    }

    @Test
    void statementsMustBeGeneratedInChronologicalOrder() {
        Statement march = TestData.statement(100, card, LocalDate.of(2026, 3, 15), "100.00");
        when(statements.findTopByCardIdOrderByPeriodEndDesc(10L)).thenReturn(Optional.of(march));

        ApiException ex = assertThrows(ApiException.class,
                () -> serviceAt(LocalDate.of(2026, 4, 20)).generate(10L, LocalDate.of(2026, 2, 15)));
        assertEquals("PERIOD_OUT_OF_ORDER", ex.getCode());
    }

    @Test
    void closedCardCannotBeBilled() {
        card.changeStatus(CardStatus.CLOSED);
        ApiException ex = assertThrows(ApiException.class,
                () -> serviceAt(LocalDate.of(2026, 3, 20)).generate(10L, LocalDate.of(2026, 3, 15)));
        assertEquals("CARD_CLOSED", ex.getCode());
    }

    @Test
    void blockedCardIsStillBilled() {
        card.changeStatus(CardStatus.BLOCKED);
        when(entries.findUnbilledUpTo(10L, LocalDate.of(2026, 3, 15))).thenReturn(firstCycleEntries());
        StatementResponse r = serviceAt(LocalDate.of(2026, 3, 20)).generate(10L, LocalDate.of(2026, 3, 15));
        assertEquals(bd("12000.00"), r.totalAmountDue());
    }

    @Test
    void periodBeforeCardWasOpenedIsRejected() {
        Customer customer = TestData.customer(1);
        CreditCard fresh = TestData.card(11, customer, bin, LocalDate.of(2026, 3, 18));
        when(cards.findByIdForUpdate(11L)).thenReturn(Optional.of(fresh));
        ApiException ex = assertThrows(ApiException.class,
                () -> serviceAt(LocalDate.of(2026, 3, 20)).generate(11L, LocalDate.of(2026, 3, 15)));
        assertEquals("PERIOD_BEFORE_CARD_OPENED", ex.getCode());
    }

    @Test
    void unknownCardIsNotFound() {
        ApiException ex = assertThrows(ApiException.class,
                () -> serviceAt(LocalDate.of(2026, 3, 20)).generate(999L, LocalDate.of(2026, 3, 15)));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void historicalStatementKeepsItsRuleSnapshotWhenBinRulesChangeLater() {
        when(entries.findUnbilledUpTo(10L, LocalDate.of(2026, 3, 15))).thenReturn(firstCycleEntries());
        ArgumentCaptorHolder holder = new ArgumentCaptorHolder();
        when(statements.save(any(Statement.class))).thenAnswer(inv -> holder.capture(inv.getArgument(0)));

        serviceAt(LocalDate.of(2026, 3, 20)).generate(10L, LocalDate.of(2026, 3, 15));
        BillingRules changed = new BillingRules(15, 20, bd("5"), bd("200"), bd("3.5"), bd("28"), bd("10"),
                bd("500"), bd("1000"), bd("500"));
        assertEquals(true, bin.update("Test BIN", changed));

        assertEquals(1, holder.statement.getRuleVersion());
        assertEquals(0, bd("18").compareTo(holder.statement.getRules().gstRate()));
        assertEquals(0, bd("100").compareTo(holder.statement.getRules().lateFeeFloor()));
        assertEquals(2, bin.getRuleVersion());
    }

    private static final class ArgumentCaptorHolder {
        Statement statement;

        Statement capture(Statement s) {
            this.statement = s;
            return s;
        }
    }
}
