package com.example.ccbilling.repository;

import com.example.ccbilling.domain.AllocationType;
import com.example.ccbilling.domain.PaymentAllocation;
import com.example.ccbilling.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Append-only: only save and read operations are exposed. */
public interface PaymentAllocationRepository extends Repository<PaymentAllocation, Long> {

    PaymentAllocation save(PaymentAllocation allocation);

    /**
     * Sum of still-valid (not reversed) payment allocations to a statement completed strictly before the cut-off.
     * Returns null when there are none.
     */
    @Query("select sum(a.amount) from PaymentAllocation a where a.statement.id = :statementId "
            + "and a.allocationType = :type and a.payment.status = :paymentStatus and a.payment.completedAt < :cutoff")
    BigDecimal sumValidAllocationsBefore(@Param("statementId") Long statementId,
                                         @Param("type") AllocationType type,
                                         @Param("paymentStatus") PaymentStatus paymentStatus,
                                         @Param("cutoff") LocalDateTime cutoff);
}
