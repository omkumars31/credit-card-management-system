package com.example.ccbilling.service;

import static com.example.ccbilling.support.TestData.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ccbilling.domain.BillingEntry;
import com.example.ccbilling.domain.CardStatus;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.EntryType;
import com.example.ccbilling.dto.CardDtos.CardBalanceResponse;
import com.example.ccbilling.dto.CardDtos.EntryResponse;
import com.example.ccbilling.dto.CardDtos.PostEntryRequest;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.BillingEntryRepository;
import com.example.ccbilling.repository.CreditCardRepository;
import com.example.ccbilling.support.MutableClock;
import com.example.ccbilling.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class BillingEntryServiceTest {

    @Mock CreditCardRepository cards;
    @Mock BillingEntryRepository entries;
    @Mock CardBalanceService balances;
    @Mock AuditService audit;

    BillingEntryService service;
    CreditCard card;

    @BeforeEach
    void setUp() {
        service = new BillingEntryService(cards, entries, balances, audit, MutableClock.at(LocalDate.of(2026, 3, 20)));
        card = TestData.card(10, TestData.customer(1), TestData.bin(1), LocalDate.of(2026, 1, 10));
        when(cards.findByIdForUpdate(10L)).thenReturn(Optional.of(card));
        when(entries.save(any(BillingEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        availableCredit("1000.00");
    }

    private void availableCredit(String amount) {
        when(balances.balanceOf(card)).thenReturn(new CardBalanceResponse(10L, bd("100000.00"), null, bd("0.00"),
                bd("0.00"), bd("0.00"), bd(amount)));
    }

    private PostEntryRequest purchase(String amount, String date, String ref) {
        return new PostEntryRequest(EntryType.PURCHASE, bd(amount), "  Coffee shop ", date == null ? null : LocalDate.parse(date), ref);
    }

    @Test
    void purchaseWithinAvailableCreditIsPostedWithDefaultsApplied() {
        EntryResponse r = service.post(10L, purchase("1000.00", null, null));

        assertEquals(bd("1000.00"), r.amount()); // exactly the available credit is allowed
        assertEquals(LocalDate.of(2026, 3, 20), r.transactionDate());
        assertNotNull(r.referenceId());
        assertEquals(null, r.statementId());
        verify(entries).save(any(BillingEntry.class));
    }

    @Test
    void purchaseAboveAvailableCreditIsRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> service.post(10L, purchase("1000.01", null, "r1")));
        assertEquals("CREDIT_LIMIT_EXCEEDED", ex.getCode());
        verify(entries, never()).save(any(BillingEntry.class));
    }

    @Test
    void refundIsNotLimitChecked() {
        availableCredit("0.00");
        EntryResponse r = service.post(10L, new PostEntryRequest(EntryType.REFUND, bd("50.00"), "Refund", null, "ref-1"));
        assertEquals(EntryType.REFUND, r.entryType());
    }

    @Test
    void blockedCardRejectsPurchasesButAcceptsFeesAndRefunds() {
        card.changeStatus(CardStatus.BLOCKED);
        assertEquals("CARD_NOT_ACTIVE",
                assertThrows(ApiException.class, () -> service.post(10L, purchase("10.00", null, "r1"))).getCode());
        service.post(10L, new PostEntryRequest(EntryType.FEE, bd("10.00"), "Annual fee", null, "fee-1"));
        service.post(10L, new PostEntryRequest(EntryType.REFUND, bd("10.00"), "Refund", null, "ref-1"));
    }

    @Test
    void closedCardRejectsEverything() {
        card.changeStatus(CardStatus.CLOSED);
        assertEquals("CARD_CLOSED",
                assertThrows(ApiException.class, () -> service.post(10L,
                        new PostEntryRequest(EntryType.REFUND, bd("1.00"), "x", null, null))).getCode());
    }

    @Test
    void duplicateReferenceIsRejected() {
        when(entries.existsByCardIdAndReferenceId(10L, "dup-1")).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class, () -> service.post(10L, purchase("10.00", null, "dup-1")));
        assertEquals("DUPLICATE_REFERENCE", ex.getCode());
        verify(entries, never()).save(any(BillingEntry.class));
    }

    @Test
    void futureAndPreOpeningDatesAreRejected() {
        assertEquals("TRANSACTION_DATE_IN_FUTURE",
                assertThrows(ApiException.class, () -> service.post(10L, purchase("10.00", "2026-03-21", "a1"))).getCode());
        assertEquals("TRANSACTION_BEFORE_CARD_OPENED",
                assertThrows(ApiException.class, () -> service.post(10L, purchase("10.00", "2026-01-09", "a2"))).getCode());
        // boundaries are accepted: today and the opening day
        service.post(10L, purchase("10.00", "2026-03-20", "a3"));
        service.post(10L, purchase("10.00", "2026-01-10", "a4"));
    }

    @Test
    void unknownCardIsNotFound() {
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> service.post(99L, purchase("1.00", null, "x"))).getCode());
    }

    @Test
    void customerCannotListEntriesOfSomeoneElsesCard() {
        when(cards.findByIdAndCustomerId(10L, 2L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class,
                () -> service.listForCustomer(2L, 10L, org.springframework.data.domain.Pageable.unpaged()));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void amountsAreStoredWithScaleTwo() {
        BigDecimal amount = service.post(10L, new PostEntryRequest(EntryType.FEE, bd("5.5"), "Fee", null, "f1")).amount();
        assertEquals(bd("5.50"), amount);
    }
}
