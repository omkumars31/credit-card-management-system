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
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A synthetic credit card. The full card number is never stored: only a mask, last 4 digits and a SHA-256 hash. */
@Entity
@Table(name = "credit_cards")
public class CreditCard extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bin_configuration_id", nullable = false, updatable = false)
    private BinConfiguration binConfiguration;

    @Column(name = "masked_number", nullable = false, length = 19, updatable = false)
    private String maskedNumber;

    @Column(nullable = false, length = 4, updatable = false)
    private String last4;

    @Column(name = "number_hash", nullable = false, length = 64, updatable = false)
    private String numberHash;

    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal creditLimit;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CardStatus status;

    @Column(name = "opened_on", nullable = false, updatable = false)
    private LocalDate openedOn;

    @Column(name = "expires_on", nullable = false, updatable = false)
    private LocalDate expiresOn;

    @Version
    private Integer version;

    protected CreditCard() {
    }

    public CreditCard(Customer customer, BinConfiguration binConfiguration, String maskedNumber, String last4,
                      String numberHash, BigDecimal creditLimit, LocalDate openedOn, LocalDate expiresOn) {
        this.customer = customer;
        this.binConfiguration = binConfiguration;
        this.maskedNumber = maskedNumber;
        this.last4 = last4;
        this.numberHash = numberHash;
        this.creditLimit = creditLimit;
        this.status = CardStatus.ACTIVE;
        this.openedOn = openedOn;
        this.expiresOn = expiresOn;
    }

    public void changeStatus(CardStatus status) { this.status = status; }

    public void changeCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }

    public Long getId() { return id; }

    public Customer getCustomer() { return customer; }

    public BinConfiguration getBinConfiguration() { return binConfiguration; }

    public String getMaskedNumber() { return maskedNumber; }

    public String getLast4() { return last4; }

    public String getNumberHash() { return numberHash; }

    public BigDecimal getCreditLimit() { return creditLimit; }

    public CardStatus getStatus() { return status; }

    public LocalDate getOpenedOn() { return openedOn; }

    public LocalDate getExpiresOn() { return expiresOn; }
}
