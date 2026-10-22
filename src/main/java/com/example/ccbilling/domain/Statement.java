package com.example.ccbilling.domain;

import com.example.ccbilling.billing.BillingCalculator;
import com.example.ccbilling.billing.BillingRules;
import com.example.ccbilling.billing.StatementFigures;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A generated statement. Figures and the rule snapshot are written once; only paidAmount and paymentStatus
 * change afterwards (through applyPayment / reversePayment).
 */
@Entity
@Table(name = "statements")
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false, updatable = false)
    private CreditCard card;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bin_configuration_id", nullable = false, updatable = false)
    private BinConfiguration binConfiguration;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private int ruleVersion;

    @Embedded
    private RuleSet rules;

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "generated_by", nullable = false, length = 190, updatable = false)
    private String generatedBy;

    @Column(name = "previous_balance", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal previousBalance;

    @Column(name = "purchases_total", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal purchasesTotal;

    @Column(name = "credits_total", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal creditsTotal;

    @Column(name = "fees_total", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal feesTotal;

    @Column(name = "finance_charge", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal financeCharge;

    @Column(name = "gst_on_finance_charge", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal gstOnFinanceCharge;

    @Column(name = "total_amount_due", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalAmountDue;

    @Column(name = "minimum_amount_due", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal minimumAmountDue;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_status", nullable = false, length = 20)
    private StatementPaymentStatus paymentStatus;

    @Version
    private Integer version;

    protected Statement() {
    }

    public Statement(CreditCard card, BinConfiguration bin, BillingRules rules, int ruleVersion,
                     LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate,
                     LocalDateTime generatedAt, String generatedBy, StatementFigures f) {
        this.card = card;
        this.binConfiguration = bin;
        this.rules = new RuleSet(rules);
        this.ruleVersion = ruleVersion;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.dueDate = dueDate;
        this.generatedAt = generatedAt;
        this.generatedBy = generatedBy;
        this.previousBalance = f.previousBalance();
        this.purchasesTotal = f.purchasesTotal();
        this.creditsTotal = f.creditsTotal();
        this.feesTotal = f.feesTotal();
        this.financeCharge = f.financeCharge();
        this.gstOnFinanceCharge = f.gstOnFinanceCharge();
        this.totalAmountDue = f.totalAmountDue();
        this.minimumAmountDue = f.minimumAmountDue();
        this.paidAmount = BigDecimal.ZERO.setScale(2);
        this.paymentStatus = BillingCalculator.paymentStatus(totalAmountDue, paidAmount);
    }

    /** Amount still to be paid (negative means the customer holds a credit balance). */
    public BigDecimal outstanding() {
        return totalAmountDue.subtract(paidAmount);
    }

    public void applyPayment(BigDecimal amount) {
        this.paidAmount = paidAmount.add(amount);
        this.paymentStatus = BillingCalculator.paymentStatus(totalAmountDue, paidAmount);
    }

    public void reversePayment(BigDecimal amount) {
        this.paidAmount = paidAmount.subtract(amount);
        this.paymentStatus = BillingCalculator.paymentStatus(totalAmountDue, paidAmount);
    }

    public Long getId() { return id; }

    public CreditCard getCard() { return card; }

    public BinConfiguration getBinConfiguration() { return binConfiguration; }

    public int getRuleVersion() { return ruleVersion; }

    public BillingRules getRules() { return rules.toRules(); }

    public LocalDate getPeriodStart() { return periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }

    public LocalDate getDueDate() { return dueDate; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }

    public String getGeneratedBy() { return generatedBy; }

    public BigDecimal getPreviousBalance() { return previousBalance; }

    public BigDecimal getPurchasesTotal() { return purchasesTotal; }

    public BigDecimal getCreditsTotal() { return creditsTotal; }

    public BigDecimal getFeesTotal() { return feesTotal; }

    public BigDecimal getFinanceCharge() { return financeCharge; }

    public BigDecimal getGstOnFinanceCharge() { return gstOnFinanceCharge; }

    public BigDecimal getTotalAmountDue() { return totalAmountDue; }

    public BigDecimal getMinimumAmountDue() { return minimumAmountDue; }

    public BigDecimal getPaidAmount() { return paidAmount; }

    public StatementPaymentStatus getPaymentStatus() { return paymentStatus; }
}
