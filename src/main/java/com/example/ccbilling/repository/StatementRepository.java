package com.example.ccbilling.repository;

import com.example.ccbilling.domain.Statement;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatementRepository extends JpaRepository<Statement, Long>, JpaSpecificationExecutor<Statement> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Statement s where s.id = :id")
    Optional<Statement> findByIdForUpdate(@Param("id") Long id);

    Optional<Statement> findTopByCardIdOrderByPeriodEndDesc(Long cardId);

    boolean existsByCardIdAndPeriodEnd(Long cardId, LocalDate periodEnd);

    Optional<Statement> findByIdAndCardCustomerId(Long id, Long customerId);

    @Override
    @EntityGraph(attributePaths = {"card"})
    Page<Statement> findAll(Specification<Statement> spec, Pageable pageable);
}
