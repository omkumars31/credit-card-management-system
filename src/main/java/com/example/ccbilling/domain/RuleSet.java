package com.example.ccbilling.domain;

import com.example.ccbilling.billing.BillingRules;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** Persistent form of {@link BillingRules}; embedded in BIN configurations and (as a snapshot) in statements. */
@Embeddable
public class RuleSet {

    @Column(name = "billing_cycle_day", nullable = false)
    private int billingCycleDay;

    @Column(name = "payment_due_days", nullable = false)
    private int paymentDueDays;

    @Column(name = "min_due_percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal minDuePercentage;

    @Column(name = "min_due_floor", nullable = false, precision = 19, scale = 2)
    private BigDecimal minDueFloor;

    @Column(name = "finance_charge_monthly_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal financeChargeMonthlyRate;

    @Column(name = "gst_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal gstRate;

    @Column(name = "late_fee_percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal lateFeePercentage;

    @Column(name = "late_fee_floor", nullable = false, precision = 19, scale = 2)
    private BigDecimal lateFeeFloor;

    @Column(name = "late_fee_cap", nullable = false, precision = 19, scale = 2)
    private BigDecimal lateFeeCap;

    @Column(name = "late_fee_threshold", nullable = false, precision = 19, scale = 2)
    private BigDecimal lateFeeThreshold;

    protected RuleSet() {
    }

    public RuleSet(BillingRules r) {
        this.billingCycleDay = r.billingCycleDay();
        this.paymentDueDays = r.paymentDueDays();
        this.minDuePercentage = r.minDuePercentage();
        this.minDueFloor = r.minDueFloor();
        this.financeChargeMonthlyRate = r.financeChargeMonthlyRate();
        this.gstRate = r.gstRate();
        this.lateFeePercentage = r.lateFeePercentage();
        this.lateFeeFloor = r.lateFeeFloor();
        this.lateFeeCap = r.lateFeeCap();
        this.lateFeeThreshold = r.lateFeeThreshold();
    }

    public BillingRules toRules() {
        return new BillingRules(billingCycleDay, paymentDueDays, minDuePercentage, minDueFloor,
                financeChargeMonthlyRate, gstRate, lateFeePercentage, lateFeeFloor, lateFeeCap, lateFeeThreshold);
    }
}
