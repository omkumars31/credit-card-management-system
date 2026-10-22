package com.example.ccbilling.service;

import static com.example.ccbilling.support.TestData.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.BinConfiguration;
import com.example.ccbilling.domain.CardStatus;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.Customer;
import com.example.ccbilling.domain.CustomerStatus;
import com.example.ccbilling.dto.CardDtos.CardBalanceResponse;
import com.example.ccbilling.dto.CardDtos.CardResponse;
import com.example.ccbilling.dto.CardDtos.CreateCardRequest;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.BinConfigurationRepository;
import com.example.ccbilling.repository.CreditCardRepository;
import com.example.ccbilling.repository.CustomerRepository;
import com.example.ccbilling.support.MutableClock;
import com.example.ccbilling.support.TestData;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditCardServiceTest {

    @Mock CreditCardRepository cards;
    @Mock CustomerRepository customers;
    @Mock BinConfigurationRepository bins;
    @Mock CardBalanceService balances;
    @Mock AuditService audit;

    CreditCardService service;
    Customer customer;
    BinConfiguration bin;
    CreditCard card;

    @BeforeEach
    void setUp() {
        service = new CreditCardService(cards, customers, bins, balances, audit,
                MutableClock.at(LocalDate.of(2026, 3, 20)), new Random(7));
        customer = TestData.customer(1);
        bin = TestData.bin(1);
        card = TestData.card(10, customer, bin, LocalDate.of(2026, 1, 10));
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        when(bins.findById(1L)).thenReturn(Optional.of(bin));
        when(cards.save(any(CreditCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cards.findByIdForUpdate(10L)).thenReturn(Optional.of(card));
        balance("0.00", "0.00", "0.00");
    }

    private void balance(String stmtOutstanding, String unbilled, String exposure) {
        when(balances.balanceOf(card)).thenReturn(new CardBalanceResponse(10L, bd("100000.00"), null,
                bd(stmtOutstanding), bd(unbilled), bd(exposure), bd("100000.00")));
    }

    @Test
    void issuedCardExposesOnlyAMaskedNumberAndStoresOnlyHashAndMask() {
        CardResponse r = service.issue(new CreateCardRequest(1L, 1L, bd("50000.00")));

        assertTrue(r.maskedNumber().matches("\\d{6}\\*{6}\\d{4}"), r.maskedNumber());
        assertTrue(r.maskedNumber().startsWith(bin.getBinCode()));
        assertEquals(CardStatus.ACTIVE, r.status());
        assertEquals(LocalDate.of(2031, 3, 31), r.expiresOn());

        ArgumentCaptor<CreditCard> saved = ArgumentCaptor.forClass(CreditCard.class);
        verify(cards).save(saved.capture());
        assertEquals(64, saved.getValue().getNumberHash().length());
        assertEquals(r.maskedNumber().substring(12), saved.getValue().getLast4());
        verify(audit).record(ArgumentMatchers.eq(AuditAction.CARD_CREATED), ArgumentMatchers.eq("CreditCard"), any(),
                ArgumentMatchers.contains("card=" + r.maskedNumber()));
    }

    @Test
    void cardsAreOnlyIssuedToActiveCustomers() {
        customer.changeStatus(CustomerStatus.DISABLED);
        ApiException ex = assertThrows(ApiException.class, () -> service.issue(new CreateCardRequest(1L, 1L, bd("1000.00"))));
        assertEquals("CUSTOMER_NOT_ACTIVE", ex.getCode());
    }

    @Test
    void numberCollisionsAreRetriedAndGiveUpAfterFiveAttempts() {
        when(cards.existsByNumberHash(any())).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class, () -> service.issue(new CreateCardRequest(1L, 1L, bd("1000.00"))));
        assertEquals("CARD_NUMBER_GENERATION_FAILED", ex.getCode());
    }

    @Test
    void validStatusTransitionsAreApplied() {
        assertEquals(CardStatus.BLOCKED, service.changeStatus(10L, CardStatus.BLOCKED).status());
        assertEquals(CardStatus.ACTIVE, service.changeStatus(10L, CardStatus.ACTIVE).status());
    }

    @Test
    void invalidTransitionIsRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> service.changeStatus(10L, CardStatus.ACTIVE));
        assertEquals("INVALID_CARD_TRANSITION", ex.getCode());
    }

    @Test
    void closedCardCanNeverBeReopened() {
        service.changeStatus(10L, CardStatus.CLOSED);
        ApiException ex = assertThrows(ApiException.class, () -> service.changeStatus(10L, CardStatus.ACTIVE));
        assertEquals("INVALID_CARD_TRANSITION", ex.getCode());
    }

    @Test
    void cardWithOutstandingOrUnbilledBalanceCannotBeClosed() {
        balance("100.00", "0.00", "100.00");
        assertEquals("CARD_HAS_BALANCE",
                assertThrows(ApiException.class, () -> service.changeStatus(10L, CardStatus.CLOSED)).getCode());
        balance("0.00", "25.00", "25.00");
        assertEquals("CARD_HAS_BALANCE",
                assertThrows(ApiException.class, () -> service.changeStatus(10L, CardStatus.CLOSED)).getCode());
        assertEquals(CardStatus.ACTIVE, card.getStatus());
    }

    @Test
    void creditLimitCannotDropBelowCurrentExposure() {
        balance("0.00", "0.00", "5000.00");
        ApiException ex = assertThrows(ApiException.class, () -> service.changeCreditLimit(10L, bd("4999.99")));
        assertEquals("LIMIT_BELOW_EXPOSURE", ex.getCode());
        assertEquals(bd("100000.00"), card.getCreditLimit());

        CardResponse ok = service.changeCreditLimit(10L, bd("5000.00")); // equal to exposure is fine
        assertEquals(bd("5000.00"), ok.creditLimit());
    }

    @Test
    void customerCanOnlySeeOwnCards() {
        when(cards.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.of(card));
        when(cards.findByIdAndCustomerId(10L, 2L)).thenReturn(Optional.empty());

        assertEquals(10L, service.getForCustomer(1L, 10L).id());
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> service.getForCustomer(2L, 10L)).getCode());
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> service.balanceForCustomer(2L, 10L)).getCode());
        assertFalse(service.listForCustomer(2L).iterator().hasNext());
    }
}
