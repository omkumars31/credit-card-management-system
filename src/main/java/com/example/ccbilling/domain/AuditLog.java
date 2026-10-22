package com.example.ccbilling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only audit record. Never contains passwords, session ids or full card numbers. */
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "actor_type", nullable = false, length = 20, updatable = false)
    private ActorType actorType;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "actor_name", length = 190, updatable = false)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 60, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 40, updatable = false)
    private String entityType;

    @Column(name = "entity_id", length = 64, updatable = false)
    private String entityId;

    @Column(length = 1000, updatable = false)
    private String details;

    protected AuditLog() {
    }

    public AuditLog(LocalDateTime occurredAt, ActorType actorType, Long actorId, String actorName,
                    AuditAction action, String entityType, String entityId, String details) {
        this.occurredAt = occurredAt;
        this.actorType = actorType;
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }

    public Long getId() { return id; }

    public LocalDateTime getOccurredAt() { return occurredAt; }

    public ActorType getActorType() { return actorType; }

    public Long getActorId() { return actorId; }

    public String getActorName() { return actorName; }

    public AuditAction getAction() { return action; }

    public String getEntityType() { return entityType; }

    public String getEntityId() { return entityId; }

    public String getDetails() { return details; }
}
