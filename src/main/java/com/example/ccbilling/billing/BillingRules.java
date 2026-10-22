package com.example.ccbilling.billing;

import java.math.BigDecimal;

/**
 * The complete set of configurable billing parameters of a BIN. Percentages are expressed as plain
 * percent values (3.5 means 3.5%).
 *
 * @param billingCycleDay           day of month (1-28) on which the billing period closes
 * @param paymentDueDays            days between the statement date and the payment due date (1-27)
 * @param minDuePercentage          percentage of the total amount due that forms the minimum amount due
 * @param minDueFloor               lowest minimum amount due (the whole balance is due if it is lower)
 * @param financeChargeMonthlyRate  monthly finance charge on the unpaid previous balance
 * @param gstRate                   GST on finance charges and late fees
 * @param lateFeePercentage         percentage of the unpaid minimum due charged as late fee
 * @param lateFeeFloor              lowest late fee
 * @param lateFeeCap                highest late fee
 * @param lateFeeThreshold          no late fee if the statement total is below this amount
 */
public record BillingRules(
        int billingCycleDay,
        int paymentDueDays,
        BigDecimal minDuePercentage,
        BigDecimal minDueFloor,
        BigDecimal financeChargeMonthlyRate,
        BigDecimal gstRate,
        BigDecimal lateFeePercentage,
        BigDecimal lateFeeFloor,
        BigDecimal lateFeeCap,
        BigDecimal lateFeeThreshold) {

    /** Value comparison that ignores BigDecimal scale (5 equals 5.0000). */
    public boolean sameAs(BillingRules o) {
        return billingCycleDay == o.billingCycleDay && paymentDueDays == o.paymentDueDays
                && minDuePercentage.compareTo(o.minDuePercentage) == 0
                && minDueFloor.compareTo(o.minDueFloor) == 0
                && financeChargeMonthlyRate.compareTo(o.financeChargeMonthlyRate) == 0
                && gstRate.compareTo(o.gstRate) == 0
                && lateFeePercentage.compareTo(o.lateFeePercentage) == 0
                && lateFeeFloor.compareTo(o.lateFeeFloor) == 0
                && lateFeeCap.compareTo(o.lateFeeCap) == 0
                && lateFeeThreshold.compareTo(o.lateFeeThreshold) == 0;
    }

    /** Human readable single-line snapshot used in the late-fee ledger and audit details. */
    public String snapshot(int ruleVersion) {
        return "ruleVersion=" + ruleVersion
                + ";cycleDay=" + billingCycleDay
                + ";dueDays=" + paymentDueDays
                + ";minDuePct=" + minDuePercentage.toPlainString()
                + ";minDueFloor=" + minDueFloor.toPlainString()
                + ";financeRateMonthlyPct=" + financeChargeMonthlyRate.toPlainString()
                + ";gstPct=" + gstRate.toPlainString()
                + ";lateFeePct=" + lateFeePercentage.toPlainString()
                + ";lateFeeFloor=" + lateFeeFloor.toPlainString()
                + ";lateFeeCap=" + lateFeeCap.toPlainString()
                + ";lateFeeThreshold=" + lateFeeThreshold.toPlainString();
    }
}
