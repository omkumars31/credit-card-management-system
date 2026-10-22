package com.example.ccbilling.repository;

import com.example.ccbilling.domain.BillingEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingEntryRepository extends JpaRepository<BillingEntry, Long> {

    boolean existsByCardIdAndReferenceId(Long cardId, String referenceId);

    Page<BillingEntry> findByCardId(Long cardId, Pageable pageable);

    List<BillingEntry> findByCardIdAndStatementIsNull(Long cardId);

    /** Entries not yet billed whose transaction date is on or before the period end. */
    @Query("select e from BillingEntry e where e.card.id = :cardId and e.statement is null "
            + "and e.transactionDate <= :periodEnd order by e.transactionDate, e.id")
    List<BillingEntry> findUnbilledUpTo(@Param("cardId") Long cardId, @Param("periodEnd") LocalDate periodEnd);
}
