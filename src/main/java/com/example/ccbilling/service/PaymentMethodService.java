package com.example.ccbilling.service;

import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.PaymentMethod;
import com.example.ccbilling.dto.PaymentDtos.PaymentMethodRequest;
import com.example.ccbilling.dto.PaymentDtos.PaymentMethodResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.CustomerRepository;
import com.example.ccbilling.repository.PaymentMethodRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {

    private final PaymentMethodRepository methods;
    private final CustomerRepository customers;
    private final AuditService audit;

    public PaymentMethodService(PaymentMethodRepository methods, CustomerRepository customers, AuditService audit) {
        this.methods = methods;
        this.customers = customers;
        this.audit = audit;
    }

    @Transactional
    public PaymentMethodResponse add(Long customerId, PaymentMethodRequest request) {
        var customer = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Customer"));
        PaymentMethod saved = methods.save(new PaymentMethod(customer, request.type(), request.displayName().trim(),
                mask(request.identifier().trim())));
        audit.record(AuditAction.PAYMENT_METHOD_ADDED, "PaymentMethod", saved.getId(),
                "type=" + saved.getMethodType() + ";identifier=" + saved.getMaskedIdentifier());
        return PaymentMethodResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> list(Long customerId) {
        return methods.findByCustomerIdOrderByIdAsc(customerId).stream().map(PaymentMethodResponse::from).toList();
    }

    /** Payment methods are deactivated, never deleted, because past payments reference them. */
    @Transactional
    public PaymentMethodResponse deactivate(Long customerId, Long methodId) {
        PaymentMethod method = methods.findByIdAndCustomerId(methodId, customerId)
                .orElseThrow(() -> ApiException.notFound("Payment method"));
        if (method.isActive()) {
            method.deactivate();
            audit.record(AuditAction.PAYMENT_METHOD_DEACTIVATED, "PaymentMethod", methodId, "deactivated");
        }
        return PaymentMethodResponse.from(method);
    }

    /** Keeps only the last four characters; the original identifier is never stored. */
    static String mask(String identifier) {
        int keep = Math.min(4, identifier.length());
        return "*".repeat(identifier.length() - keep) + identifier.substring(identifier.length() - keep);
    }
}
