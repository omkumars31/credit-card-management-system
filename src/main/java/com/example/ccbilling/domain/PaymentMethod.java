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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A customer's saved (simulated) payment instrument. Only a masked identifier is stored. */
@Entity
@Table(name = "payment_methods")
public class PaymentMethod extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "method_type", nullable = false, length = 20, updatable = false)
    private PaymentMethodType methodType;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "masked_identifier", nullable = false, length = 60, updatable = false)
    private String maskedIdentifier;

    @Column(nullable = false)
    private boolean active;

    @Version
    private Integer version;

    protected PaymentMethod() {
    }

    public PaymentMethod(Customer customer, PaymentMethodType methodType, String displayName, String maskedIdentifier) {
        this.customer = customer;
        this.methodType = methodType;
        this.displayName = displayName;
        this.maskedIdentifier = maskedIdentifier;
        this.active = true;
    }

    public void deactivate() { this.active = false; }

    public Long getId() { return id; }

    public Customer getCustomer() { return customer; }

    public PaymentMethodType getMethodType() { return methodType; }

    public String getDisplayName() { return displayName; }

    public String getMaskedIdentifier() { return maskedIdentifier; }

    public boolean isActive() { return active; }
}
