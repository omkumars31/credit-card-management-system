package com.example.ccbilling.repository;

import com.example.ccbilling.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/** Append-only: only save and read operations are exposed (no update or delete). */
public interface AuditLogRepository extends Repository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    AuditLog save(AuditLog log);

    long count();
}
