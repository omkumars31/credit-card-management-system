package com.example.ccbilling.service;

import com.example.ccbilling.billing.CardNumbers;
import com.example.ccbilling.billing.Money;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.BinConfiguration;
import com.example.ccbilling.domain.CardStatus;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.Customer;
import com.example.ccbilling.domain.CustomerStatus;
import com.example.ccbilling.dto.CardDtos.CardBalanceResponse;
import com.example.ccbilling.dto.CardDtos.CardResponse;
import com.example.ccbilling.dto.CardDtos.CreateCardRequest;
import com.example.ccbilling.dto.PageResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.BinConfigurationRepository;
import com.example.ccbilling.repository.CreditCardRepository;
import com.example.ccbilling.repository.CustomerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditCardService {

    private static final int GENERATION_ATTEMPTS = 5;
    private static final int VALIDITY_YEARS = 5;

    private final CreditCardRepository cards;
    private final CustomerRepository customers;
    private final BinConfigurationRepository bins;
    private final CardBalanceService balances;
    private final AuditService audit;
    private final Clock clock;
    private final Random random;

    public CreditCardService(CreditCardRepository cards, CustomerRepository customers, BinConfigurationRepository bins,
                             CardBalanceService balances, AuditService audit, Clock clock, Random cardNumberRandom) {
        this.cards = cards;
        this.customers = customers;
        this.bins = bins;
        this.balances = balances;
        this.audit = audit;
        this.clock = clock;
        this.random = cardNumberRandom;
    }

    // ---- administrator operations ----

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CardResponse issue(CreateCardRequest request) {
        Customer customer = customers.findById(request.customerId()).orElseThrow(() -> ApiException.notFound("Customer"));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw ApiException.unprocessable("CUSTOMER_NOT_ACTIVE", "Cards can only be issued to active customers");
        }
        BinConfiguration bin = bins.findById(request.binConfigurationId())
                .orElseThrow(() -> ApiException.notFound("BIN configuration"));

        String pan = null;
        String hash = null;
        for (int i = 0; i < GENERATION_ATTEMPTS && pan == null; i++) {
            String candidate = CardNumbers.generate(bin.getBinCode(), random);
            String candidateHash = CardNumbers.sha256(candidate);
            if (!cards.existsByNumberHash(candidateHash)) {
                pan = candidate;
                hash = candidateHash;
            }
        }
        if (pan == null) {
            throw ApiException.conflict("CARD_NUMBER_GENERATION_FAILED", "Could not allocate a unique card number, retry");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate expiry = today.plusYears(VALIDITY_YEARS).withDayOfMonth(1).plusMonths(1).minusDays(1);
        // The full number exists only in this method; only mask, last4 and hash are persisted.
        CreditCard card = cards.save(new CreditCard(customer, bin, CardNumbers.mask(pan), CardNumbers.last4(pan), hash,
                Money.scale(request.creditLimit()), today, expiry));
        audit.record(AuditAction.CARD_CREATED, "CreditCard", card.getId(),
                "customerId=" + customer.getId() + ";bin=" + bin.getBinCode() + ";card=" + card.getMaskedNumber()
                        + ";limit=" + card.getCreditLimit().toPlainString());
        return CardResponse.from(card);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CardResponse changeStatus(Long cardId, CardStatus target) {
        CreditCard card = cards.findByIdForUpdate(cardId).orElseThrow(() -> ApiException.notFound("Card"));
        CardStatus current = card.getStatus();
        if (!current.canTransitionTo(target)) {
            throw ApiException.unprocessable("INVALID_CARD_TRANSITION",
                    "Card cannot move from " + current + " to " + target);
        }
        if (target == CardStatus.CLOSED) {
            CardBalanceResponse b = balances.balanceOf(card);
            if (b.statementOutstanding().signum() != 0 || b.unbilledNet().signum() != 0) {
                throw ApiException.unprocessable("CARD_HAS_BALANCE",
                        "A card can only be closed when it has no outstanding or unbilled balance");
            }
        }
        card.changeStatus(target);
        audit.record(AuditAction.CARD_STATUS_CHANGED, "CreditCard", cardId, "status " + current + "->" + target);
        return CardResponse.from(card);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CardResponse changeCreditLimit(Long cardId, BigDecimal newLimit) {
        CreditCard card = cards.findByIdForUpdate(cardId).orElseThrow(() -> ApiException.notFound("Card"));
        if (card.getStatus() == CardStatus.CLOSED) {
            throw ApiException.unprocessable("CARD_CLOSED", "The credit limit of a closed card cannot be changed");
        }
        BigDecimal exposure = balances.balanceOf(card).totalExposure();
        BigDecimal scaled = Money.scale(newLimit);
        if (scaled.compareTo(exposure) < 0) {
            throw ApiException.unprocessable("LIMIT_BELOW_EXPOSURE", "The new credit limit is below the current exposure");
        }
        BigDecimal old = card.getCreditLimit();
        card.changeCreditLimit(scaled);
        audit.record(AuditAction.CARD_LIMIT_CHANGED, "CreditCard", cardId,
                "limit " + old.toPlainString() + "->" + scaled.toPlainString());
        return CardResponse.from(card);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<CardResponse> search(Long customerId, CardStatus status, Pageable pageable) {
        Specification<CreditCard> spec = (root, query, cb) -> cb.conjunction();
        if (customerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return PageResponse.of(cards.findAll(spec, pageable), CardResponse::from);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public CardResponse get(Long cardId) {
        return CardResponse.from(cards.findById(cardId).orElseThrow(() -> ApiException.notFound("Card")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public CardBalanceResponse balance(Long cardId) {
        return balances.balanceOf(cards.findById(cardId).orElseThrow(() -> ApiException.notFound("Card")));
    }

    // ---- customer operations (ownership enforced here, not only in the controller) ----

    @Transactional(readOnly = true)
    public List<CardResponse> listForCustomer(Long customerId) {
        return cards.findByCustomerIdOrderByIdAsc(customerId).stream().map(CardResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CardResponse getForCustomer(Long customerId, Long cardId) {
        return CardResponse.from(requireOwned(customerId, cardId));
    }

    @Transactional(readOnly = true)
    public CardBalanceResponse balanceForCustomer(Long customerId, Long cardId) {
        return balances.balanceOf(requireOwned(customerId, cardId));
    }

    CreditCard requireOwned(Long customerId, Long cardId) {
        return cards.findByIdAndCustomerId(cardId, customerId).orElseThrow(() -> ApiException.notFound("Card"));
    }
}
