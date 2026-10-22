package com.example.ccbilling.service;

import com.example.ccbilling.billing.CardNumbers;
import com.example.ccbilling.billing.Money;
import com.example.ccbilling.domain.AllocationType;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.Payment;
import com.example.ccbilling.domain.PaymentAllocation;
import com.example.ccbilling.domain.PaymentMethod;
import com.example.ccbilling.domain.PaymentStatus;
import com.example.ccbilling.domain.Statement;
import com.example.ccbilling.dto.PageResponse;
import com.example.ccbilling.dto.PaymentDtos.PaymentRequest;
import com.example.ccbilling.dto.PaymentDtos.PaymentResponse;
import com.example.ccbilling.dto.PaymentDtos.PaymentResult;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.PaymentAllocationRepository;
import com.example.ccbilling.repository.PaymentMethodRepository;
import com.example.ccbilling.repository.PaymentRepository;
import com.example.ccbilling.repository.StatementRepository;
import com.example.ccbilling.security.Actor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulated payments. Every state change (payment row, allocation row, statement balance, audit row) happens in one
 * database transaction. The statement row is locked first so concurrent payments/reversals on a statement are
 * serialised; (customer, idempotency key) is unique as a backstop.
 */
@Service
public class PaymentService {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final PaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final PaymentMethodRepository methods;
    private final StatementRepository statements;
    private final AuditService audit;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentAllocationRepository allocations,
                          PaymentMethodRepository methods, StatementRepository statements, AuditService audit,
                          Clock clock) {
        this.payments = payments;
        this.allocations = allocations;
        this.methods = methods;
        this.statements = statements;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PaymentResult pay(Long customerId, String idempotencyKey, PaymentRequest request) {
        if (idempotencyKey == null || !KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY",
                    "Header Idempotency-Key is required: 8-64 characters, letters, digits, '-' and '_'");
        }
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0 || !Money.hasAtMostTwoDecimals(amount)) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Amount must be positive with at most 2 decimal places");
        }
        amount = Money.scale(amount);

        Statement statement = statements.findByIdForUpdate(request.statementId())
                .orElseThrow(() -> ApiException.notFound("Statement"));
        if (!statement.getCard().getCustomer().getId().equals(customerId)) {
            throw ApiException.notFound("Statement"); // do not reveal that it exists
        }

        String fingerprint = CardNumbers.sha256(statement.getId() + "|" + request.paymentMethodId() + "|"
                + amount.toPlainString());
        Optional<Payment> existing = payments.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestFingerprint().equals(fingerprint)) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used with a different request");
            }
            return new PaymentResult(PaymentResponse.from(existing.get()), true);
        }

        PaymentMethod method = methods.findByIdAndCustomerId(request.paymentMethodId(), customerId)
                .orElseThrow(() -> ApiException.notFound("Payment method"));
        if (!method.isActive()) {
            throw ApiException.unprocessable("PAYMENT_METHOD_INACTIVE", "The payment method has been deactivated");
        }
        requireLatestStatement(statement, "STATEMENT_SUPERSEDED",
                "A newer statement exists for this card; pay the latest statement");

        BigDecimal outstanding = statement.outstanding();
        if (outstanding.signum() <= 0) {
            throw ApiException.unprocessable("NOTHING_DUE", "This statement has no outstanding amount");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw ApiException.unprocessable("PAYMENT_EXCEEDS_OUTSTANDING",
                    "Payment exceeds the outstanding amount of " + outstanding.toPlainString());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Payment payment = payments.save(new Payment(statement.getCard().getCustomer(), statement, method,
                idempotencyKey, fingerprint, amount, now));
        allocations.save(new PaymentAllocation(payment, statement, AllocationType.ALLOCATION, amount, now));
        statement.applyPayment(amount);

        audit.record(AuditAction.PAYMENT_COMPLETED, "Payment", payment.getId(),
                "statement=" + statement.getId() + ";amount=" + amount.toPlainString()
                        + ";statementStatus=" + statement.getPaymentStatus());
        return new PaymentResult(PaymentResponse.from(payment), false);
    }

    /**
     * Reversal policy: administrator only; allowed once per payment and only while the payment's statement is still
     * the card's latest statement. The payment becomes REVERSED, a negative allocation row is appended and the
     * statement balance is restored. Late fees already assessed are not reversed automatically.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PaymentResponse reverse(Long paymentId, String reason) {
        Long statementId = payments.findStatementIdById(paymentId).orElseThrow(() -> ApiException.notFound("Payment"));
        Statement statement = statements.findByIdForUpdate(statementId).orElseThrow(() -> ApiException.notFound("Statement"));
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(() -> ApiException.notFound("Payment"));
        if (payment.getStatus() == PaymentStatus.REVERSED) {
            throw ApiException.conflict("PAYMENT_ALREADY_REVERSED", "The payment has already been reversed");
        }
        requireLatestStatement(statement, "REVERSAL_NOT_ALLOWED",
                "The payment's statement has been superseded by a newer statement");

        LocalDateTime now = LocalDateTime.now(clock);
        payment.markReversed(now, reason.trim(), Actor.current().label());
        allocations.save(new PaymentAllocation(payment, statement, AllocationType.REVERSAL,
                payment.getAmount().negate(), now));
        statement.reversePayment(payment.getAmount());

        audit.record(AuditAction.PAYMENT_REVERSED, "Payment", paymentId,
                "statement=" + statementId + ";amount=" + payment.getAmount().toPlainString()
                        + ";statementStatus=" + statement.getPaymentStatus());
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> searchForCustomer(Long customerId, Long statementId, PaymentStatus status,
                                                           Pageable pageable) {
        return search(customerId, statementId, status, pageable);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> searchAll(Long customerId, Long statementId, PaymentStatus status,
                                                   Pageable pageable) {
        return search(customerId, statementId, status, pageable);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getForCustomer(Long customerId, Long paymentId) {
        return PaymentResponse.from(payments.findByIdAndCustomerId(paymentId, customerId)
                .orElseThrow(() -> ApiException.notFound("Payment")));
    }

    private PageResponse<PaymentResponse> search(Long customerId, Long statementId, PaymentStatus status, Pageable pageable) {
        Specification<Payment> spec = (root, query, cb) -> cb.conjunction();
        if (customerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId));
        }
        if (statementId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("statement").get("id"), statementId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return PageResponse.of(payments.findAll(spec, pageable), PaymentResponse::from);
    }

    private void requireLatestStatement(Statement statement, String code, String message) {
        Optional<Statement> latest = statements.findTopByCardIdOrderByPeriodEndDesc(statement.getCard().getId());
        if (latest.isPresent() && !latest.get().getId().equals(statement.getId())) {
            throw ApiException.unprocessable(code, message);
        }
    }
}
