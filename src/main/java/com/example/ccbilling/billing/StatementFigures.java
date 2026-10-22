package com.example.ccbilling.billing;

import java.math.BigDecimal;

/** Result of the statement calculation. All amounts have scale 2. */
public record StatementFigures(
        BigDecimal previousBalance,
        BigDecimal purchasesTotal,
        BigDecimal creditsTotal,
        BigDecimal feesTotal,
        BigDecimal financeCharge,
        BigDecimal gstOnFinanceCharge,
        BigDecimal totalAmountDue,
        BigDecimal minimumAmountDue) {
}
