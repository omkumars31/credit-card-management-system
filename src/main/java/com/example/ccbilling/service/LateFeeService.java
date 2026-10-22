package com.example.ccbilling.service;

import com.example.ccbilling.billing.BillingCalculator;
import com.example.ccbilling.billing.BillingRules;
import com.example.ccbilling.billing.Money;
import com.example.ccbilling.domain.AllocationType;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.BillingEntry;
import com.example.ccbilling.domain.EntryType;
import com.example.ccbilling.domain.LateFeeLedgerEntry;
import com.example.ccbilling.domain.PaymentStatus;
import com.example.ccbilling.domain.Statement;
import com.example.ccbilling.dto.LedgerDtos.LateFeeResponse;
import com.example.ccbilling.dto.PageResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.BillingEntryRepository;
import com.example.ccbilling.repository.LateFeeLedgerRepository;
import com.example.ccbilling.repository.PaymentAllocationRepository;
import com.example.ccbilling.repository.StatementRepository;
import com.example.ccbilling.security.Actor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Late-fee assessment. A fee applies when, after the due date, the net payments completed on or before the due date
 * were below the statement's minimum amount due. The fee is computed from the rule snapshot stored on the statement,
 * written to the append-only ledger and posted to the card as a LATE_FEE entry (fee + GST) that is billed on the next
 * statement. At most one assessment per statement is possible.
 */
@Service
public class LateFeeService {

    private final StatementRepository statements;
    private final LateFeeLedgerRepository ledger;
    private final BillingEntryRepository entries;
    private final PaymentAllocationRepository allocations;
    private final AuditService audit;
    private final Clock clock;

    public LateFeeService(StatementRepository statements, LateFeeLedgerRepository ledger, BillingEntryRepository entries,
                          PaymentAllocationRepository allocations, AuditService audit, Clock clock) {
        this.statements = statements;
        this.ledger = ledger;
        this.entries = entries;
        this.allocations = allocations;
        this.audit = audit;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LateFeeResponse assess(Long statementId) {
        Statement statement = statements.findByIdForUpdate(statementId)
                .orElseThrow(() -> ApiException.notFound("Statement"));
        if (ledger.existsByStatementIdAndFeeEvent(statementId, LateFeeLedgerEntry.EVENT_LATE_PAYMENT)) {
            throw ApiException.conflict("LATE_FEE_ALREADY_ASSESSED", "A late fee was already assessed for this statement");
        }
        LocalDate today = LocalDate.now(clock);
        if (!today.isAfter(statement.getDueDate())) {
            throw ApiException.unprocessable("NOT_OVERDUE", "The statement is not past its due date yet");
        }
        BigDecimal paidOnTime = allocations.sumValidAllocationsBefore(statementId, AllocationType.ALLOCATION,
                PaymentStatus.COMPLETED, statement.getDueDate().plusDays(1).atStartOfDay());
        if (paidOnTime == null) {
            paidOnTime = Money.ZERO;
        }
        BigDecimal unpaidMinimum = statement.getMinimumAmountDue().subtract(paidOnTime);
        if (unpaidMinimum.signum() <= 0) {
            throw ApiException.unprocessable("MINIMUM_DUE_SATISFIED", "The minimum amount due was paid on time");
        }
        BillingRules rules = statement.getRules();
        BigDecimal fee = BillingCalculator.lateFee(unpaidMinimum, statement.getTotalAmountDue(), rules);
        if (fee.signum() <= 0) {
            throw ApiException.unprocessable("NO_LATE_FEE_APPLICABLE",
                    "No late fee applies under the statement's billing rules");
        }
        BigDecimal gst = BillingCalculator.gst(fee, rules.gstRate());
        BigDecimal total = fee.add(gst);
        LocalDateTime now = LocalDateTime.now(clock);
        String actor = Actor.current().label();

        BillingEntry entry = entries.save(new BillingEntry(statement.getCard(), "LATEFEE-" + statementId,
                EntryType.LATE_FEE, total, "Late payment fee incl. GST for statement #" + statementId, today, now, actor));
        LateFeeLedgerEntry row = ledger.save(new LateFeeLedgerEntry(statement, statement.getCard(), entry, fee,
                rules.gstRate(), gst, unpaidMinimum, statement.getRuleVersion(),
                rules.snapshot(statement.getRuleVersion()), now, actor));

        audit.record(AuditAction.LATE_FEE_ASSESSED, "LateFeeLedger", row.getId(),
                "statement=" + statementId + ";fee=" + fee.toPlainString() + ";gst=" + gst.toPlainString()
                        + ";unpaidMinDue=" + unpaidMinimum.toPlainString());
        return LateFeeResponse.from(row);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<LateFeeResponse> search(Long statementId, Long cardId, Pageable pageable) {
        return search(null, statementId, cardId, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<LateFeeResponse> searchForCustomer(Long customerId, Long statementId, Long cardId, Pageable pageable) {
        return search(customerId, statementId, cardId, pageable);
    }

    private PageResponse<LateFeeResponse> search(Long customerId, Long statementId, Long cardId, Pageable pageable) {
        Specification<LateFeeLedgerEntry> spec = (root, query, cb) -> cb.conjunction();
        if (customerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("card").get("customer").get("id"), customerId));
        }
        if (statementId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("statement").get("id"), statementId));
        }
        if (cardId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("card").get("id"), cardId));
        }
        return PageResponse.of(ledger.findAll(spec, pageable), LateFeeResponse::from);
    }
}
