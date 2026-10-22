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
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A simulated card transaction/fee/refund. Immutable once posted, except for being assigned to a statement. */
@Entity
@Table(name = "billing_entries")
public class BillingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false, updatable = false)
    private CreditCard card;

    /** Null until the entry has been billed on a statement. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    private Statement statement;

    @Column(name = "reference_id", nullable = false, length = 64, updatable = false)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "entry_type", nullable = false, length = 20, updatable = false)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 255, updatable = false)
    private String description;

    @Column(name = "transaction_date", nullable = false, updatable = false)
    private LocalDate transactionDate;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private LocalDateTime postedAt;

    @Column(name = "created_by", nullable = false, length = 190, updatable = false)
    private String createdBy;

    protected BillingEntry() {
    }

    public BillingEntry(CreditCard card, String referenceId, EntryType entryType, BigDecimal amount,
                        String description, LocalDate transactionDate, LocalDateTime postedAt, String createdBy) {
        this.card = card;
        this.referenceId = referenceId;
        this.entryType = entryType;
        this.amount = amount;
        this.description = description;
        this.transactionDate = transactionDate;
        this.postedAt = postedAt;
        this.createdBy = createdBy;
    }

    public void assignTo(Statement statement) { this.statement = statement; }

    public Long getId() { return id; }

    public CreditCard getCard() { return card; }

    public Statement getStatement() { return statement; }

    public String getReferenceId() { return referenceId; }

    public EntryType getEntryType() { return entryType; }

    public BigDecimal getAmount() { return amount; }

    public String getDescription() { return description; }

    public LocalDate getTransactionDate() { return transactionDate; }

    public LocalDateTime getPostedAt() { return postedAt; }

    public String getCreatedBy() { return createdBy; }
}
