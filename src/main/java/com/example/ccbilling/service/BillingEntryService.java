package com.example.ccbilling.service;

import com.example.ccbilling.billing.Money;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.BillingEntry;
import com.example.ccbilling.domain.CardStatus;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.EntryType;
import com.example.ccbilling.dto.CardDtos.EntryResponse;
import com.example.ccbilling.dto.CardDtos.PostEntryRequest;
import com.example.ccbilling.dto.PageResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.BillingEntryRepository;
import com.example.ccbilling.repository.CreditCardRepository;
import com.example.ccbilling.security.Actor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Simulated card transaction feed: purchases, fees and refunds posted by administrators. */
@Service
public class BillingEntryService {

    private final CreditCardRepository cards;
    private final BillingEntryRepository entries;
    private final CardBalanceService balances;
    private final AuditService audit;
    private final Clock clock;

    public BillingEntryService(CreditCardRepository cards, BillingEntryRepository entries,
                               CardBalanceService balances, AuditService audit, Clock clock) {
        this.cards = cards;
        this.entries = entries;
        this.balances = balances;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public EntryResponse post(Long cardId, PostEntryRequest request) {
        CreditCard card = cards.findByIdForUpdate(cardId).orElseThrow(() -> ApiException.notFound("Card"));
        if (card.getStatus() == CardStatus.CLOSED) {
            throw ApiException.unprocessable("CARD_CLOSED", "Entries cannot be posted to a closed card");
        }
        if (request.entryType() == EntryType.PURCHASE && card.getStatus() != CardStatus.ACTIVE) {
            throw ApiException.unprocessable("CARD_NOT_ACTIVE", "Purchases require an ACTIVE card");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate date = request.transactionDate() == null ? today : request.transactionDate();
        if (date.isAfter(today)) {
            throw ApiException.unprocessable("TRANSACTION_DATE_IN_FUTURE", "Transaction date cannot be in the future");
        }
        if (date.isBefore(card.getOpenedOn())) {
            throw ApiException.unprocessable("TRANSACTION_BEFORE_CARD_OPENED", "Transaction date precedes the card opening date");
        }
        BigDecimal amount = Money.scale(request.amount());
        String reference = request.referenceId() == null ? UUID.randomUUID().toString() : request.referenceId();
        if (entries.existsByCardIdAndReferenceId(cardId, reference)) {
            throw ApiException.conflict("DUPLICATE_REFERENCE", "An entry with this referenceId already exists for the card");
        }
        if (request.entryType() == EntryType.PURCHASE
                && amount.compareTo(balances.balanceOf(card).availableCredit()) > 0) {
            throw ApiException.unprocessable("CREDIT_LIMIT_EXCEEDED", "The purchase exceeds the available credit");
        }
        BillingEntry saved = entries.save(new BillingEntry(card, reference, request.entryType(), amount,
                request.description().trim(), date, LocalDateTime.now(clock), Actor.current().label()));
        audit.record(AuditAction.BILLING_ENTRY_POSTED, "BillingEntry", saved.getId(),
                "card=" + card.getMaskedNumber() + ";type=" + request.entryType() + ";amount=" + amount.toPlainString()
                        + ";ref=" + reference);
        return EntryResponse.from(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<EntryResponse> listForCard(Long cardId, Pageable pageable) {
        if (!cards.existsById(cardId)) {
            throw ApiException.notFound("Card");
        }
        return PageResponse.of(entries.findByCardId(cardId, pageable), EntryResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<EntryResponse> listForCustomer(Long customerId, Long cardId, Pageable pageable) {
        cards.findByIdAndCustomerId(cardId, customerId).orElseThrow(() -> ApiException.notFound("Card"));
        return PageResponse.of(entries.findByCardId(cardId, pageable), EntryResponse::from);
    }
}
