package com.example.ccbilling.dto;

import com.example.ccbilling.billing.BillingRules;
import com.example.ccbilling.domain.BinConfiguration;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class BinDtos {

    private BinDtos() {
    }

    public record BinConfigRequest(
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be exactly 6 digits") String binCode,
            @NotBlank @Size(max = 100) String name,
            @NotNull @Min(1) @Max(28) Integer billingCycleDay,
            @NotNull @Min(1) @Max(27) Integer paymentDueDays,
            @NotNull @DecimalMin(value = "0.0001") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal minDuePercentage,
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal minDueFloor,
            @NotNull @DecimalMin("0") @DecimalMax("25") @Digits(integer = 3, fraction = 4) BigDecimal financeChargeMonthlyRate,
            @NotNull @DecimalMin("0") @DecimalMax("50") @Digits(integer = 3, fraction = 4) BigDecimal gstRate,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal lateFeePercentage,
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal lateFeeFloor,
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal lateFeeCap,
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal lateFeeThreshold) {

        public BillingRules toRules() {
            return new BillingRules(billingCycleDay, paymentDueDays, minDuePercentage, minDueFloor,
                    financeChargeMonthlyRate, gstRate, lateFeePercentage, lateFeeFloor, lateFeeCap, lateFeeThreshold);
        }
    }

    public record BinConfigResponse(Long id, String binCode, String name, BillingRules rules, int ruleVersion,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static BinConfigResponse from(BinConfiguration b) {
            return new BinConfigResponse(b.getId(), b.getBinCode(), b.getName(), b.toRules(), b.getRuleVersion(),
                    b.getCreatedAt(), b.getUpdatedAt());
        }
    }
}
