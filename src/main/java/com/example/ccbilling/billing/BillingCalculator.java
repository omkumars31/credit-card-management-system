package com.example.ccbilling.billing;

import com.example.ccbilling.domain.StatementPaymentStatus;
import java.math.BigDecimal;

/**
 * All billing formulas in one framework-free place (documented in the README).
 *
 * <pre>
 * financeCharge      = round2( max(0, previousBalance) * monthlyRate / 100 )
 * gstOnFinanceCharge = round2( financeCharge * gstRate / 100 )
 * totalAmountDue     = previousBalance + purchases + fees + financeCharge + gstOnFinanceCharge - credits
 * minimumAmountDue   = 0                                   if totalAmountDue <= 0
 *                    = totalAmountDue                      if totalAmountDue <= minDueFloor
 *                    = max(minDueFloor, round2(total * minDuePct / 100))   otherwise
 * lateFee            = 0                                   if unpaidMinimumDue <= 0 or totalAmountDue < threshold
 *                    = min(cap, max(floor, round2(unpaidMinimumDue * lateFeePct / 100)))   otherwise
 * gstOnLateFee       = round2( lateFee * gstRate / 100 )
 * </pre>
 * Rounding is HALF_UP at scale 2, applied once per derived amount.
 */
public final class BillingCalculator {

    private BillingCalculator() {
    }

    public static BigDecimal financeCharge(BigDecimal previousBalance, BigDecimal monthlyRatePercent) {
        BigDecimal base = previousBalance.signum() > 0 ? previousBalance : BigDecimal.ZERO;
        return Money.percentOf(base, monthlyRatePercent);
    }

    public static BigDecimal gst(BigDecimal taxableAmount, BigDecimal gstRatePercent) {
        if (taxableAmount.signum() <= 0) {
            return Money.ZERO;
        }
        return Money.percentOf(taxableAmount, gstRatePercent);
    }

    public static BigDecimal minimumDue(BigDecimal totalAmountDue, BigDecimal percentage, BigDecimal floor) {
        if (totalAmountDue.signum() <= 0) {
            return Money.ZERO;
        }
        BigDecimal scaledFloor = Money.scale(floor);
        if (totalAmountDue.compareTo(scaledFloor) <= 0) {
            return Money.scale(totalAmountDue);
        }
        BigDecimal byPercentage = Money.percentOf(totalAmountDue, percentage);
        return Money.min(Money.max(byPercentage, scaledFloor), totalAmountDue);
    }

    public static BigDecimal lateFee(BigDecimal unpaidMinimumDue, BigDecimal totalAmountDue, BillingRules rules) {
        if (unpaidMinimumDue.signum() <= 0 || totalAmountDue.compareTo(rules.lateFeeThreshold()) < 0) {
            return Money.ZERO;
        }
        BigDecimal raw = Money.percentOf(unpaidMinimumDue, rules.lateFeePercentage());
        BigDecimal bounded = Money.min(Money.max(raw, Money.scale(rules.lateFeeFloor())), Money.scale(rules.lateFeeCap()));
        return Money.scale(bounded);
    }

    public static StatementFigures computeStatement(BigDecimal previousBalance, BigDecimal purchases,
                                                    BigDecimal credits, BigDecimal fees, BillingRules rules) {
        BigDecimal previous = Money.scale(previousBalance);
        BigDecimal financeCharge = financeCharge(previous, rules.financeChargeMonthlyRate());
        BigDecimal gstOnFinance = gst(financeCharge, rules.gstRate());
        BigDecimal total = previous.add(purchases).add(fees).add(financeCharge).add(gstOnFinance).subtract(credits);
        total = Money.scale(total);
        BigDecimal minimum = minimumDue(total, rules.minDuePercentage(), rules.minDueFloor());
        return new StatementFigures(previous, Money.scale(purchases), Money.scale(credits), Money.scale(fees),
                financeCharge, gstOnFinance, total, minimum);
    }

    public static StatementPaymentStatus paymentStatus(BigDecimal totalAmountDue, BigDecimal paidAmount) {
        if (totalAmountDue.subtract(paidAmount).signum() <= 0) {
            return StatementPaymentStatus.PAID;
        }
        return paidAmount.signum() > 0 ? StatementPaymentStatus.PARTIALLY_PAID : StatementPaymentStatus.UNPAID;
    }
}
