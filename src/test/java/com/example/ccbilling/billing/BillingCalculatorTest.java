package com.example.ccbilling.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.ccbilling.domain.StatementPaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BillingCalculatorTest {

    // cycle 15, due 20 days, min due 5% (floor 200), finance 3.5%/month, GST 18%,
    // late fee 10% of unpaid minimum (floor 100, cap 1000), threshold 500
    private static final BillingRules RULES = new BillingRules(15, 20, bd("5"), bd("200"), bd("3.5"),
            bd("18"), bd("10"), bd("100"), bd("1000"), bd("500"));

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    // ---- finance charge ----

    @Test
    void financeChargeIsRateOnPositivePreviousBalance() {
        assertEquals(bd("409.50"), BillingCalculator.financeCharge(bd("11700.00"), bd("3.5")));
    }

    @Test
    void financeChargeIsZeroWhenPreviousBalanceIsZeroOrCredit() {
        assertEquals(bd("0.00"), BillingCalculator.financeCharge(bd("0.00"), bd("3.5")));
        assertEquals(bd("0.00"), BillingCalculator.financeCharge(bd("-250.00"), bd("3.5")));
    }

    @Test
    void financeChargeRoundsHalfUpAtTwoDecimals() {
        // 0.15 * 3.5 / 100 = 0.00525 -> 0.01 ; 0.10 * 3.5 / 100 = 0.0035 -> 0.00
        assertEquals(bd("0.01"), BillingCalculator.financeCharge(bd("0.15"), bd("3.5")));
        assertEquals(bd("0.00"), BillingCalculator.financeCharge(bd("0.10"), bd("3.5")));
        // 1234.57 * 3.5 / 100 = 43.20995 -> 43.21
        assertEquals(bd("43.21"), BillingCalculator.financeCharge(bd("1234.57"), bd("3.5")));
    }

    // ---- GST ----

    @Test
    void gstIsAppliedToTheTaxableAmount() {
        assertEquals(bd("73.71"), BillingCalculator.gst(bd("409.50"), bd("18")));
        assertEquals(bd("18.00"), BillingCalculator.gst(bd("100.00"), bd("18")));
    }

    @Test
    void gstIsZeroForZeroAmountOrZeroRate() {
        assertEquals(bd("0.00"), BillingCalculator.gst(bd("0.00"), bd("18")));
        assertEquals(bd("0.00"), BillingCalculator.gst(bd("500.00"), bd("0")));
    }

    @Test
    void gstRoundsHalfUp() {
        // 0.25 * 18 / 100 = 0.045 -> 0.05
        assertEquals(bd("0.05"), BillingCalculator.gst(bd("0.25"), bd("18")));
    }

    // ---- minimum amount due ----

    @Test
    void minimumDueIsPercentageWhenAboveFloor() {
        assertEquals(bd("600.00"), BillingCalculator.minimumDue(bd("12000.00"), bd("5"), bd("200")));
        assertEquals(bd("665.06"), BillingCalculator.minimumDue(bd("13301.21"), bd("5"), bd("200")));
    }

    @Test
    void minimumDueUsesFloorWhenPercentageIsLower() {
        // 5% of 3000 = 150 < floor 200
        assertEquals(bd("200.00"), BillingCalculator.minimumDue(bd("3000.00"), bd("5"), bd("200")));
    }

    @Test
    void minimumDueBoundaryAroundFloorAndPercentageCrossover() {
        // exactly the floor -> whole balance
        assertEquals(bd("200.00"), BillingCalculator.minimumDue(bd("200.00"), bd("5"), bd("200")));
        // just above the floor -> floor still wins until 5% exceeds 200 (i.e. balance 4000)
        assertEquals(bd("200.00"), BillingCalculator.minimumDue(bd("200.01"), bd("5"), bd("200")));
        assertEquals(bd("200.00"), BillingCalculator.minimumDue(bd("4000.00"), bd("5"), bd("200")));
        assertEquals(bd("200.05"), BillingCalculator.minimumDue(bd("4001.00"), bd("5"), bd("200")));
    }

    @Test
    void minimumDueNeverExceedsTotalAndIsZeroForNonPositiveTotals() {
        assertEquals(bd("150.00"), BillingCalculator.minimumDue(bd("150.00"), bd("5"), bd("200")));
        assertEquals(bd("50.00"), BillingCalculator.minimumDue(bd("50.00"), bd("100"), bd("0")));
        assertEquals(bd("0.00"), BillingCalculator.minimumDue(bd("0.00"), bd("5"), bd("200")));
        assertEquals(bd("0.00"), BillingCalculator.minimumDue(bd("-10.00"), bd("5"), bd("200")));
    }

    // ---- late fee ----

    @Test
    void lateFeeUsesFloorWhenPercentageIsLower() {
        // 10% of 300 = 30 -> floor 100
        assertEquals(bd("100.00"), BillingCalculator.lateFee(bd("300.00"), bd("12000.00"), RULES));
    }

    @Test
    void lateFeeUsesPercentageBetweenFloorAndCap() {
        // 10% of 5000 = 500
        assertEquals(bd("500.00"), BillingCalculator.lateFee(bd("5000.00"), bd("60000.00"), RULES));
    }

    @Test
    void lateFeeIsCapped() {
        // 10% of 50000 = 5000 -> cap 1000
        assertEquals(bd("1000.00"), BillingCalculator.lateFee(bd("50000.00"), bd("900000.00"), RULES));
        // boundary: 10% of 10000 = 1000 exactly the cap
        assertEquals(bd("1000.00"), BillingCalculator.lateFee(bd("10000.00"), bd("90000.00"), RULES));
    }

    @Test
    void lateFeeThresholdBoundary() {
        // total just below the threshold -> no fee; exactly at threshold -> fee applies
        assertEquals(bd("0.00"), BillingCalculator.lateFee(bd("300.00"), bd("499.99"), RULES));
        assertEquals(bd("100.00"), BillingCalculator.lateFee(bd("300.00"), bd("500.00"), RULES));
    }

    @Test
    void lateFeeIsZeroWhenMinimumDueIsSatisfied() {
        assertEquals(bd("0.00"), BillingCalculator.lateFee(bd("0.00"), bd("12000.00"), RULES));
        assertEquals(bd("0.00"), BillingCalculator.lateFee(bd("-5.00"), bd("12000.00"), RULES));
    }

    // ---- full statement ----

    @Test
    void firstStatementHasNoFinanceCharge() {
        StatementFigures f = BillingCalculator.computeStatement(bd("0"), bd("12500.50"), bd("500.50"), bd("0"), RULES);
        assertEquals(bd("0.00"), f.financeCharge());
        assertEquals(bd("0.00"), f.gstOnFinanceCharge());
        assertEquals(bd("12000.00"), f.totalAmountDue());
        assertEquals(bd("600.00"), f.minimumAmountDue());
    }

    @Test
    void secondStatementCarriesBalanceFinanceChargeGstAndFees() {
        StatementFigures f = BillingCalculator.computeStatement(bd("11700.00"), bd("1000.00"), bd("0"), bd("118.00"), RULES);
        assertEquals(bd("11700.00"), f.previousBalance());
        assertEquals(bd("409.50"), f.financeCharge());
        assertEquals(bd("73.71"), f.gstOnFinanceCharge());
        assertEquals(bd("13301.21"), f.totalAmountDue());
        assertEquals(bd("665.06"), f.minimumAmountDue());
    }

    @Test
    void creditsLargerThanChargesGiveCreditBalanceAndZeroMinimumDue() {
        StatementFigures f = BillingCalculator.computeStatement(bd("0"), bd("100.00"), bd("350.00"), bd("0"), RULES);
        assertEquals(bd("-250.00"), f.totalAmountDue());
        assertEquals(bd("0.00"), f.minimumAmountDue());
    }

    @Test
    void creditBalanceCarriedForwardOffsetsNextStatementWithoutFinanceCharge() {
        StatementFigures f = BillingCalculator.computeStatement(bd("-250.00"), bd("1000.00"), bd("0"), bd("0"), RULES);
        assertEquals(bd("0.00"), f.financeCharge());
        assertEquals(bd("750.00"), f.totalAmountDue());
    }

    // ---- payment status ----

    @Test
    void paymentStatusFollowsPaidAmount() {
        assertEquals(StatementPaymentStatus.UNPAID, BillingCalculator.paymentStatus(bd("100.00"), bd("0.00")));
        assertEquals(StatementPaymentStatus.PARTIALLY_PAID, BillingCalculator.paymentStatus(bd("100.00"), bd("0.01")));
        assertEquals(StatementPaymentStatus.PARTIALLY_PAID, BillingCalculator.paymentStatus(bd("100.00"), bd("99.99")));
        assertEquals(StatementPaymentStatus.PAID, BillingCalculator.paymentStatus(bd("100.00"), bd("100.00")));
        assertEquals(StatementPaymentStatus.PAID, BillingCalculator.paymentStatus(bd("-5.00"), bd("0.00")));
    }
}
