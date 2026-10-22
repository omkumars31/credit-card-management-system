package com.example.ccbilling.dto;

import com.example.ccbilling.domain.Administrator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record CreateAdminRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,60}$", message = "3-60 characters: letters, digits, . _ -") String username,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record AdminResponse(Long id, String username, String fullName, boolean enabled, LocalDateTime createdAt) {
        public static AdminResponse from(Administrator a) {
            return new AdminResponse(a.getId(), a.getUsername(), a.getFullName(), a.isEnabled(), a.getCreatedAt());
        }
    }

    public record SetEnabledRequest(@NotNull Boolean enabled) {
    }
}
