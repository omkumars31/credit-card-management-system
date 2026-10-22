package com.example.ccbilling.service;

import com.example.ccbilling.billing.BillingCalculator;
import com.example.ccbilling.billing.BillingRules;
import com.example.ccbilling.billing.Money;
import com.example.ccbilling.billing.StatementFigures;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.BillingEntry;
import com.example.ccbilling.domain.BinConfiguration;
import com.example.ccbilling.domain.CardStatus;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.EntryType;
import com.example.ccbilling.domain.Statement;
import com.example.ccbilling.domain.StatementPaymentStatus;
import com.example.ccbilling.dto.PageResponse;
import com.example.ccbilling.dto.StatementDtos.StatementResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.BillingEntryRepository;
import com.example.ccbilling.repository.CreditCardRepository;
import com.example.ccbilling.repository.StatementRepository;
import com.example.ccbilling.security.Actor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementService {

    private final CreditCardRepository cards;
    private final StatementRepository statements;
    private final BillingEntryRepository entries;
    private final AuditService audit;
    private final Clock clock;

    public StatementService(CreditCardRepository cards, StatementRepository statements, BillingEntryRepository entries,
                            AuditService audit, Clock clock) {
        this.cards = cards;
        this.statements = statements;
        this.entries = entries;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Generates the statement for the billing period that ends on {@code periodEnd}. The card row is locked first, so
     * concurrent requests for the same card are serialised; the unique (card, period) constraint is a second line of
     * defence. A repeated request fails with 409 and never creates a second set of charges.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public StatementResponse generate(Long cardId, LocalDate periodEnd) {
        CreditCard card = cards.findByIdForUpdate(cardId).orElseThrow(() -> ApiException.notFound("Card"));
        if (card.getStatus() == CardStatus.CLOSED) {
            throw ApiException.unprocessable("CARD_CLOSED", "Statements cannot be generated for a closed card");
        }
        BinConfiguration bin = card.getBinConfiguration();
        BillingRules rules = bin.toRules();
        LocalDate today = LocalDate.now(clock);

        if (periodEnd.isAfter(today)) {
            throw ApiException.unprocessable("PERIOD_NOT_ENDED", "The billing period has not ended yet");
        }
        if (periodEnd.getDayOfMonth() != rules.billingCycleDay()) {
            throw ApiException.unprocessable("INVALID_PERIOD_END",
                    "periodEnd must fall on the BIN billing cycle day (" + rules.billingCycleDay() + ")");
        }
        if (periodEnd.isBefore(card.getOpenedOn())) {
            throw ApiException.unprocessable("PERIOD_BEFORE_CARD_OPENED", "The period ends before the card was opened");
        }
        if (statements.existsByCardIdAndPeriodEnd(cardId, periodEnd)) {
            throw ApiException.conflict("STATEMENT_ALREADY_EXISTS", "A statement already exists for this card and period");
        }
        Optional<Statement> latest = statements.findTopByCardIdOrderByPeriodEndDesc(cardId);
        if (latest.isPresent() && !periodEnd.isAfter(latest.get().getPeriodEnd())) {
            throw ApiException.unprocessable("PERIOD_OUT_OF_ORDER",
                    "periodEnd must be after the latest statement period end (" + latest.get().getPeriodEnd() + ")");
        }
        LocalDate periodStart = latest.map(s -> s.getPeriodEnd().plusDays(1))
                .orElseGet(() -> periodEnd.minusMonths(1).plusDays(1));

        List<BillingEntry> unbilled = entries.findUnbilledUpTo(cardId, periodEnd);
        BigDecimal purchases = sum(unbilled, EntryType.PURCHASE);
        BigDecimal credits = sum(unbilled, EntryType.REFUND);
        BigDecimal fees = sum(unbilled, EntryType.FEE).add(sum(unbilled, EntryType.LATE_FEE));
        BigDecimal previous = latest.map(Statement::outstanding).orElse(Money.ZERO);

        StatementFigures figures = BillingCalculator.computeStatement(previous, purchases, credits, fees, rules);
        Statement statement = statements.save(new Statement(card, bin, rules, bin.getRuleVersion(), periodStart,
                periodEnd, periodEnd.plusDays(rules.paymentDueDays()), LocalDateTime.now(clock),
                Actor.current().label(), figures));
        unbilled.forEach(e -> e.assignTo(statement));

        audit.record(AuditAction.STATEMENT_GENERATED, "Statement", statement.getId(),
                "card=" + card.getMaskedNumber() + ";period=" + periodStart + ".." + periodEnd
                        + ";total=" + figures.totalAmountDue().toPlainString()
                        + ";minDue=" + figures.minimumAmountDue().toPlainString()
                        + ";entries=" + unbilled.size() + ";ruleVersion=" + bin.getRuleVersion());
        return StatementResponse.from(statement);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<StatementResponse> search(Long cardId, StatementPaymentStatus status, Pageable pageable) {
        Specification<Statement> spec = filter(null, cardId, status);
        return PageResponse.of(statements.findAll(spec, pageable), StatementResponse::from);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public StatementResponse get(Long id) {
        return StatementResponse.from(statements.findById(id).orElseThrow(() -> ApiException.notFound("Statement")));
    }

    @Transactional(readOnly = true)
    public PageResponse<StatementResponse> searchForCustomer(Long customerId, Long cardId, StatementPaymentStatus status,
                                                             Pageable pageable) {
        Specification<Statement> spec = filter(customerId, cardId, status);
        return PageResponse.of(statements.findAll(spec, pageable), StatementResponse::from);
    }

    @Transactional(readOnly = true)
    public StatementResponse getForCustomer(Long customerId, Long statementId) {
        return StatementResponse.from(statements.findByIdAndCardCustomerId(statementId, customerId)
                .orElseThrow(() -> ApiException.notFound("Statement")));
    }

    private static Specification<Statement> filter(Long customerId, Long cardId, StatementPaymentStatus status) {
        Specification<Statement> spec = (root, query, cb) -> cb.conjunction();
        if (customerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("card").get("customer").get("id"), customerId));
        }
        if (cardId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("card").get("id"), cardId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("paymentStatus"), status));
        }
        return spec;
    }

    private static BigDecimal sum(List<BillingEntry> entries, EntryType type) {
        return entries.stream().filter(e -> e.getEntryType() == type).map(BillingEntry::getAmount)
                .reduce(Money.ZERO, BigDecimal::add);
    }
}
