package com.example.ccbilling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A simulated payment. It is completed immediately; the only later change is an administrative reversal. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false, updatable = false)
    private Statement statement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private LocalDateTime initiatedAt;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private LocalDateTime completedAt;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    @Column(name = "reversed_by", length = 190)
    private String reversedBy;

    @Version
    private Integer version;

    protected Payment() {
    }

    public Payment(Customer customer, Statement statement, PaymentMethod paymentMethod, String idempotencyKey,
                   String requestFingerprint, BigDecimal amount, LocalDateTime now) {
        this.customer = customer;
        this.statement = statement;
        this.paymentMethod = paymentMethod;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.amount = amount;
        this.status = PaymentStatus.COMPLETED;
        this.initiatedAt = now;
        this.completedAt = now;
    }

    public void markReversed(LocalDateTime now, String reason, String actor) {
        this.status = PaymentStatus.REVERSED;
        this.reversedAt = now;
        this.reversalReason = reason;
        this.reversedBy = actor;
    }

    public Long getId() { return id; }

    public Customer getCustomer() { return customer; }

    public Statement getStatement() { return statement; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }

    public String getIdempotencyKey() { return idempotencyKey; }

    public String getRequestFingerprint() { return requestFingerprint; }

    public BigDecimal getAmount() { return amount; }

    public PaymentStatus getStatus() { return status; }

    public LocalDateTime getInitiatedAt() { return initiatedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }

    public LocalDateTime getReversedAt() { return reversedAt; }

    public String getReversalReason() { return reversalReason; }
}
