package com.example.ccbilling.dto;

import com.example.ccbilling.security.AppPrincipal;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public enum LoginType { CUSTOMER, ADMIN }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 190) String email,
            @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be 7-15 digits, optionally starting with +") String phone,
            @NotBlank
            @Size(min = 10, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
            String password) {
    }

    public record LoginRequest(
            @NotNull LoginType userType,
            @NotBlank @Size(max = 190) String identifier,
            @NotBlank @Size(max = 72) String password) {
    }

    public record AuthUserResponse(String userType, Long id, String username, String displayName) {
        public static AuthUserResponse from(AppPrincipal p) {
            return new AuthUserResponse(p.getUserType().name(), p.getId(), p.getLoginName(), p.getDisplayName());
        }
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }
}
