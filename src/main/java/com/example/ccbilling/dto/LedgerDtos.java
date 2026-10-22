package com.example.ccbilling.dto;

import com.example.ccbilling.domain.ActorType;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.AuditLog;
import com.example.ccbilling.domain.LateFeeLedgerEntry;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record LateFeeResponse(Long id, Long statementId, Long cardId, Long billingEntryId, String feeEvent,
                                  BigDecimal feeAmount, BigDecimal gstRate, BigDecimal gstAmount,
                                  BigDecimal totalAmount, BigDecimal unpaidMinimumDue, int ruleVersion,
                                  String ruleSnapshot, String status, LocalDateTime assessedAt, String assessedBy) {
        public static LateFeeResponse from(LateFeeLedgerEntry e) {
            return new LateFeeResponse(e.getId(), e.getStatement().getId(), e.getCard().getId(),
                    e.getBillingEntry() == null ? null : e.getBillingEntry().getId(), e.getFeeEvent(),
                    e.getFeeAmount(), e.getGstRate(), e.getGstAmount(), e.getTotalAmount(), e.getUnpaidMinimumDue(),
                    e.getRuleVersion(), e.getRuleSnapshot(), e.getStatus(), e.getAssessedAt(), e.getAssessedBy());
        }
    }

    public record AuditLogResponse(Long id, LocalDateTime occurredAt, ActorType actorType, Long actorId,
                                   String actorName, AuditAction action, String entityType, String entityId,
                                   String details) {
        public static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getOccurredAt(), a.getActorType(), a.getActorId(),
                    a.getActorName(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getDetails());
        }
    }
}
