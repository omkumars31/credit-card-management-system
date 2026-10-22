package com.example.ccbilling.service;

import com.example.ccbilling.domain.ActorType;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.AuditLog;
import com.example.ccbilling.dto.LedgerDtos.AuditLogResponse;
import com.example.ccbilling.dto.PageResponse;
import com.example.ccbilling.repository.AuditLogRepository;
import com.example.ccbilling.security.Actor;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes append-only audit records inside the caller's transaction, so an audit row is committed or rolled back
 * together with the business change it describes. Callers must pass only non-sensitive details.
 */
@Service
public class AuditService {

    private static final int MAX_DETAILS = 1000;

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, String entityType, Object entityId, String details) {
        Actor actor = Actor.current();
        write(actor.type(), actor.id(), actor.name(), action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAs(ActorType type, Long actorId, String actorName, AuditAction action, String entityType,
                         Object entityId, String details) {
        write(type, actorId, actorName, action, entityType, entityId, details);
    }

    private void write(ActorType type, Long actorId, String actorName, AuditAction action, String entityType,
                       Object entityId, String details) {
        String safe = details == null ? null : (details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details);
        repository.save(new AuditLog(LocalDateTime.now(clock), type, actorId, actorName, action, entityType,
                entityId == null ? null : String.valueOf(entityId), safe));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditAction action, String entityType, String entityId,
                                                 ActorType actorType, Long actorId, LocalDateTime from,
                                                 LocalDateTime to, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (action != null) p.add(cb.equal(root.get("action"), action));
            if (entityType != null && !entityType.isBlank()) p.add(cb.equal(root.get("entityType"), entityType));
            if (entityId != null && !entityId.isBlank()) p.add(cb.equal(root.get("entityId"), entityId));
            if (actorType != null) p.add(cb.equal(root.get("actorType"), actorType));
            if (actorId != null) p.add(cb.equal(root.get("actorId"), actorId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return PageResponse.of(repository.findAll(spec, pageable), AuditLogResponse::from);
    }
}
