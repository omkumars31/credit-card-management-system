package com.example.ccbilling.repository;

import com.example.ccbilling.domain.LateFeeLedgerEntry;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/** Append-only: only save and read operations are exposed (no update or delete). */
public interface LateFeeLedgerRepository
        extends Repository<LateFeeLedgerEntry, Long>, JpaSpecificationExecutor<LateFeeLedgerEntry> {

    LateFeeLedgerEntry save(LateFeeLedgerEntry entry);

    Optional<LateFeeLedgerEntry> findById(Long id);

    boolean existsByStatementIdAndFeeEvent(Long statementId, String feeEvent);

    @Override
    @EntityGraph(attributePaths = {"statement", "card"})
    Page<LateFeeLedgerEntry> findAll(Specification<LateFeeLedgerEntry> spec, Pageable pageable);
}
