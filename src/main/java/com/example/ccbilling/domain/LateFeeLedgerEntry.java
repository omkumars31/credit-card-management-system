package com.example.ccbilling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Append-only late-fee ledger row. There is at most one row per (statement, fee event). */
@Entity
@Immutable
@Table(name = "late_fee_ledger")
public class LateFeeLedgerEntry {

    public static final String EVENT_LATE_PAYMENT = "LATE_PAYMENT";
    public static final String STATUS_POSTED = "POSTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false, updatable = false)
    private Statement statement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false, updatable = false)
    private CreditCard card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_entry_id", updatable = false)
    private BillingEntry billingEntry;

    @Column(name = "fee_event", nullable = false, length = 30, updatable = false)
    private String feeEvent;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal feeAmount;

    @Column(name = "gst_rate", nullable = false, precision = 7, scale = 4, updatable = false)
    private BigDecimal gstRate;

    @Column(name = "gst_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal gstAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "unpaid_minimum_due", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal unpaidMinimumDue;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private int ruleVersion;

    @Column(name = "rule_snapshot", nullable = false, length = 1000, updatable = false)
    private String ruleSnapshot;

    @Column(nullable = false, length = 20, updatable = false)
    private String status;

    @Column(name = "assessed_at", nullable = false, updatable = false)
    private LocalDateTime assessedAt;

    @Column(name = "assessed_by", nullable = false, length = 190, updatable = false)
    private String assessedBy;

    protected LateFeeLedgerEntry() {
    }

    public LateFeeLedgerEntry(Statement statement, CreditCard card, BillingEntry billingEntry, BigDecimal feeAmount,
                              BigDecimal gstRate, BigDecimal gstAmount, BigDecimal unpaidMinimumDue,
                              int ruleVersion, String ruleSnapshot, LocalDateTime assessedAt, String assessedBy) {
        this.statement = statement;
        this.card = card;
        this.billingEntry = billingEntry;
        this.feeEvent = EVENT_LATE_PAYMENT;
        this.feeAmount = feeAmount;
        this.gstRate = gstRate;
        this.gstAmount = gstAmount;
        this.totalAmount = feeAmount.add(gstAmount);
        this.unpaidMinimumDue = unpaidMinimumDue;
        this.ruleVersion = ruleVersion;
        this.ruleSnapshot = ruleSnapshot;
        this.status = STATUS_POSTED;
        this.assessedAt = assessedAt;
        this.assessedBy = assessedBy;
    }

    public Long getId() { return id; }

    public Statement getStatement() { return statement; }

    public CreditCard getCard() { return card; }

    public BillingEntry getBillingEntry() { return billingEntry; }

    public String getFeeEvent() { return feeEvent; }

    public BigDecimal getFeeAmount() { return feeAmount; }

    public BigDecimal getGstRate() { return gstRate; }

    public BigDecimal getGstAmount() { return gstAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }

    public BigDecimal getUnpaidMinimumDue() { return unpaidMinimumDue; }

    public int getRuleVersion() { return ruleVersion; }

    public String getRuleSnapshot() { return ruleSnapshot; }

    public String getStatus() { return status; }

    public LocalDateTime getAssessedAt() { return assessedAt; }

    public String getAssessedBy() { return assessedBy; }
}
