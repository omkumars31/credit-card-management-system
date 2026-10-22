package com.example.ccbilling.repository;

import com.example.ccbilling.domain.Payment;
import jakarta.persistence.LockModeType;
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

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    Optional<Payment> findByIdAndCustomerId(Long id, Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    /** Statement id of a payment without loading (and caching) the payment itself. */
    @Query("select p.statement.id from Payment p where p.id = :id")
    Optional<Long> findStatementIdById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"statement", "paymentMethod"})
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);
}
