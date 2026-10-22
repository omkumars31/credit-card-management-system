package com.example.ccbilling.dto;

import com.example.ccbilling.billing.BillingRules;
import com.example.ccbilling.domain.Statement;
import com.example.ccbilling.domain.StatementPaymentStatus;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class StatementDtos {

    private StatementDtos() {
    }

    public record GenerateStatementRequest(@NotNull Long cardId, @NotNull LocalDate periodEnd) {
    }

    public record StatementResponse(Long id, Long cardId, String maskedCardNumber, LocalDate periodStart,
                                    LocalDate periodEnd, LocalDate dueDate, LocalDateTime generatedAt,
                                    String generatedBy, BigDecimal previousBalance, BigDecimal purchasesTotal,
                                    BigDecimal creditsTotal, BigDecimal feesTotal, BigDecimal financeCharge,
                                    BigDecimal gstOnFinanceCharge, BigDecimal totalAmountDue,
                                    BigDecimal minimumAmountDue, BigDecimal paidAmount, BigDecimal outstandingAmount,
                                    StatementPaymentStatus paymentStatus, int ruleVersion, BillingRules rulesApplied) {
        public static StatementResponse from(Statement s) {
            return new StatementResponse(s.getId(), s.getCard().getId(), s.getCard().getMaskedNumber(),
                    s.getPeriodStart(), s.getPeriodEnd(), s.getDueDate(), s.getGeneratedAt(), s.getGeneratedBy(),
                    s.getPreviousBalance(), s.getPurchasesTotal(), s.getCreditsTotal(), s.getFeesTotal(),
                    s.getFinanceCharge(), s.getGstOnFinanceCharge(), s.getTotalAmountDue(), s.getMinimumAmountDue(),
                    s.getPaidAmount(), s.outstanding(), s.getPaymentStatus(), s.getRuleVersion(), s.getRules());
        }
    }
}
