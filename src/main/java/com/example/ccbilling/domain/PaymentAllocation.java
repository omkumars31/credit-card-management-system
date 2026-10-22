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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only record of how a payment was applied to a statement; a reversal is a new negative row. */
@Entity
@Immutable
@Table(name = "payment_allocations")
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false, updatable = false)
    private Statement statement;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "allocation_type", nullable = false, length = 20, updatable = false)
    private AllocationType allocationType;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PaymentAllocation() {
    }

    public PaymentAllocation(Payment payment, Statement statement, AllocationType allocationType,
                             BigDecimal amount, LocalDateTime createdAt) {
        this.payment = payment;
        this.statement = statement;
        this.allocationType = allocationType;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }

    public Payment getPayment() { return payment; }

    public Statement getStatement() { return statement; }

    public AllocationType getAllocationType() { return allocationType; }

    public BigDecimal getAmount() { return amount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
