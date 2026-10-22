package com.example.ccbilling.dto;

import com.example.ccbilling.domain.Payment;
import com.example.ccbilling.domain.PaymentMethod;
import com.example.ccbilling.domain.PaymentMethodType;
import com.example.ccbilling.domain.PaymentStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentRequest(
            @NotNull Long statementId,
            @NotNull Long paymentMethodId,
            @NotNull @DecimalMin("0.01") @DecimalMax("100000000.00") @Digits(integer = 9, fraction = 2) BigDecimal amount) {
    }

    public record ReversePaymentRequest(@NotBlank @Size(max = 255) String reason) {
    }

    public record PaymentResponse(Long id, Long statementId, Long paymentMethodId, BigDecimal amount,
                                  PaymentStatus status, LocalDateTime initiatedAt, LocalDateTime completedAt,
                                  LocalDateTime reversedAt, String reversalReason) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getStatement().getId(), p.getPaymentMethod().getId(),
                    p.getAmount(), p.getStatus(), p.getInitiatedAt(), p.getCompletedAt(), p.getReversedAt(),
                    p.getReversalReason());
        }
    }

    /** Outcome of a payment call: replayed=true means an earlier identical request was returned. */
    public record PaymentResult(PaymentResponse payment, boolean replayed) {
    }

    public record PaymentMethodRequest(
            @NotNull PaymentMethodType type,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(min = 4, max = 60) String identifier) {
    }

    /** Only a masked identifier is ever returned. */
    public record PaymentMethodResponse(Long id, PaymentMethodType type, String displayName, String maskedIdentifier,
                                        boolean active, LocalDateTime createdAt) {
        public static PaymentMethodResponse from(PaymentMethod m) {
            return new PaymentMethodResponse(m.getId(), m.getMethodType(), m.getDisplayName(), m.getMaskedIdentifier(),
                    m.isActive(), m.getCreatedAt());
        }
    }
}
