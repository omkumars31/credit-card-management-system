package com.example.ccbilling.dto;

import com.example.ccbilling.domain.CardStatus;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.EntryType;
import com.example.ccbilling.domain.BillingEntry;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CardDtos {

    private CardDtos() {
    }

    public record CreateCardRequest(
            @NotNull Long customerId,
            @NotNull Long binConfigurationId,
            @NotNull @DecimalMin("1.00") @DecimalMax("100000000.00") @Digits(integer = 9, fraction = 2) BigDecimal creditLimit) {
    }

    public record CardStatusRequest(@NotNull CardStatus status) {
    }

    public record CreditLimitRequest(
            @NotNull @DecimalMin("1.00") @DecimalMax("100000000.00") @Digits(integer = 9, fraction = 2) BigDecimal creditLimit) {
    }

    /** Never includes the full card number: only the masked form. */
    public record CardResponse(Long id, Long customerId, Long binConfigurationId, String binCode, String maskedNumber,
                               BigDecimal creditLimit, CardStatus status, LocalDate expiresOn, LocalDateTime createdAt) {
        public static CardResponse from(CreditCard c) {
            return new CardResponse(c.getId(), c.getCustomer().getId(), c.getBinConfiguration().getId(),
                    c.getBinConfiguration().getBinCode(), c.getMaskedNumber(), c.getCreditLimit(), c.getStatus(),
                    c.getExpiresOn(), c.getCreatedAt());
        }
    }

    public record CardBalanceResponse(Long cardId, BigDecimal creditLimit, Long latestStatementId,
                                      BigDecimal statementOutstanding, BigDecimal unbilledNet,
                                      BigDecimal totalExposure, BigDecimal availableCredit) {
    }

    public record PostEntryRequest(
            @NotNull EntryType entryType,
            @NotNull @DecimalMin("0.01") @DecimalMax("100000000.00") @Digits(integer = 9, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String description,
            LocalDate transactionDate,
            @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$", message = "1-64 characters: letters, digits, . _ -") String referenceId) {
    }

    public record EntryResponse(Long id, Long cardId, Long statementId, String referenceId, EntryType entryType,
                                BigDecimal amount, String description, LocalDate transactionDate,
                                LocalDateTime postedAt) {
        public static EntryResponse from(BillingEntry e) {
            return new EntryResponse(e.getId(), e.getCard().getId(),
                    e.getStatement() == null ? null : e.getStatement().getId(), e.getReferenceId(),
                    e.getEntryType(), e.getAmount(), e.getDescription(), e.getTransactionDate(), e.getPostedAt());
        }
    }
}
