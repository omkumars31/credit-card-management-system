package com.example.ccbilling.billing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Pure validation of billing rules, including cross-field (conflicting rule) checks. */
public final class BillingRulesValidator {

    public static final int MAX_DUE_DAYS = 27; // due date must fall before the next statement of the shortest cycle (28 days)
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_FINANCE_RATE = BigDecimal.valueOf(25);
    private static final BigDecimal MAX_GST_RATE = BigDecimal.valueOf(50);

    private BillingRulesValidator() {
    }

    /** Returns every violation found; an empty list means the rules are valid. */
    public static List<String> violations(BillingRules r) {
        List<String> v = new ArrayList<>();
        if (r.billingCycleDay() < 1 || r.billingCycleDay() > 28) {
            v.add("billingCycleDay must be between 1 and 28");
        }
        if (r.paymentDueDays() < 1 || r.paymentDueDays() > MAX_DUE_DAYS) {
            v.add("paymentDueDays must be between 1 and " + MAX_DUE_DAYS
                    + " so the due date falls before the next statement date");
        }
        if (r.minDuePercentage().signum() <= 0 || r.minDuePercentage().compareTo(HUNDRED) > 0) {
            v.add("minDuePercentage must be greater than 0 and at most 100");
        }
        nonNegative(v, "minDueFloor", r.minDueFloor());
        if (r.financeChargeMonthlyRate().signum() < 0 || r.financeChargeMonthlyRate().compareTo(MAX_FINANCE_RATE) > 0) {
            v.add("financeChargeMonthlyRate must be between 0 and 25");
        }
        if (r.gstRate().signum() < 0 || r.gstRate().compareTo(MAX_GST_RATE) > 0) {
            v.add("gstRate must be between 0 and 50");
        }
        if (r.lateFeePercentage().signum() < 0 || r.lateFeePercentage().compareTo(HUNDRED) > 0) {
            v.add("lateFeePercentage must be between 0 and 100");
        }
        nonNegative(v, "lateFeeFloor", r.lateFeeFloor());
        nonNegative(v, "lateFeeCap", r.lateFeeCap());
        nonNegative(v, "lateFeeThreshold", r.lateFeeThreshold());
        if (r.lateFeeCap().compareTo(r.lateFeeFloor()) < 0) {
            v.add("lateFeeCap must not be lower than lateFeeFloor");
        }
        for (var entry : new Object[][]{
                {"minDueFloor", r.minDueFloor()}, {"lateFeeFloor", r.lateFeeFloor()},
                {"lateFeeCap", r.lateFeeCap()}, {"lateFeeThreshold", r.lateFeeThreshold()}}) {
            if (!Money.hasAtMostTwoDecimals((BigDecimal) entry[1])) {
                v.add(entry[0] + " must have at most 2 decimal places");
            }
        }
        return v;
    }

    private static void nonNegative(List<String> v, String name, BigDecimal value) {
        if (value.signum() < 0) {
            v.add(name + " must not be negative");
        }
    }
}
