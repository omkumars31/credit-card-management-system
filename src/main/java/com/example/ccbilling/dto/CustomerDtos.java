package com.example.ccbilling.dto;

import com.example.ccbilling.domain.Customer;
import com.example.ccbilling.domain.CustomerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerResponse(Long id, String fullName, String email, String phone, CustomerStatus status,
                                   LocalDateTime createdAt) {
        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(), c.getStatus(),
                    c.getCreatedAt());
        }
    }

    public record UpdateCustomerRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be 7-15 digits, optionally starting with +") String phone) {
    }

    public record CustomerStatusRequest(@NotNull CustomerStatus status) {
    }
}
