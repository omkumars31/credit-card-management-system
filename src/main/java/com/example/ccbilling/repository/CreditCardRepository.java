package com.example.ccbilling.repository;

import com.example.ccbilling.domain.CreditCard;
import jakarta.persistence.LockModeType;
import java.util.List;
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

public interface CreditCardRepository extends JpaRepository<CreditCard, Long>, JpaSpecificationExecutor<CreditCard> {

    Optional<CreditCard> findByIdAndCustomerId(Long id, Long customerId);

    List<CreditCard> findByCustomerIdOrderByIdAsc(Long customerId);

    boolean existsByNumberHash(String numberHash);

    /** Row lock used to serialise financial operations on one card. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CreditCard c where c.id = :id")
    Optional<CreditCard> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"customer", "binConfiguration"})
    Page<CreditCard> findAll(Specification<CreditCard> spec, Pageable pageable);
}
