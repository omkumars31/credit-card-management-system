package com.example.ccbilling.service;

import com.example.ccbilling.domain.ActorType;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.Customer;
import com.example.ccbilling.domain.CustomerStatus;
import com.example.ccbilling.dto.AuthDtos.RegisterRequest;
import com.example.ccbilling.dto.CustomerDtos.CustomerResponse;
import com.example.ccbilling.dto.CustomerDtos.UpdateCustomerRequest;
import com.example.ccbilling.dto.PageResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.CustomerRepository;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public CustomerService(CustomerRepository customers, PasswordEncoder passwordEncoder, AuditService audit) {
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (customers.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }
        Customer saved = customers.save(new Customer(request.fullName().trim(), email, blankToNull(request.phone()),
                passwordEncoder.encode(request.password())));
        audit.recordAs(ActorType.CUSTOMER, saved.getId(), saved.getEmail(), AuditAction.CUSTOMER_REGISTERED,
                "Customer", saved.getId(), "email registered");
        return CustomerResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getProfile(Long customerId) {
        return CustomerResponse.from(load(customerId));
    }

    @Transactional
    public CustomerResponse updateProfile(Long customerId, UpdateCustomerRequest request) {
        Customer customer = load(customerId);
        customer.updateProfile(request.fullName().trim(), blankToNull(request.phone()));
        audit.record(AuditAction.CUSTOMER_UPDATED, "Customer", customerId, "profile updated");
        return CustomerResponse.from(customer);
    }

    // ---- administrator operations ----

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String emailContains, CustomerStatus status, Pageable pageable) {
        Specification<Customer> spec = (root, query, cb) -> cb.conjunction();
        if (emailContains != null && !emailContains.isBlank()) {
            String like = "%" + emailContains.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("email")), like));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return PageResponse.of(customers.findAll(spec, pageable), CustomerResponse::from);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public CustomerResponse get(Long customerId) {
        return CustomerResponse.from(load(customerId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CustomerResponse changeStatus(Long customerId, CustomerStatus status) {
        Customer customer = load(customerId);
        CustomerStatus old = customer.getStatus();
        customer.changeStatus(status);
        audit.record(AuditAction.CUSTOMER_STATUS_CHANGED, "Customer", customerId, "status " + old + "->" + status);
        return CustomerResponse.from(customer);
    }

    private Customer load(Long id) {
        return customers.findById(id).orElseThrow(() -> ApiException.notFound("Customer"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
