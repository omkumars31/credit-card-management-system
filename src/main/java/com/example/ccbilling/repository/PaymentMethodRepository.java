package com.example.ccbilling.repository;

import com.example.ccbilling.domain.PaymentMethod;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    Optional<PaymentMethod> findByIdAndCustomerId(Long id, Long customerId);

    List<PaymentMethod> findByCustomerIdOrderByIdAsc(Long customerId);
}
