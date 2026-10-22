package com.example.ccbilling.service;

import com.example.ccbilling.domain.Administrator;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.dto.AdminDtos.AdminResponse;
import com.example.ccbilling.dto.AdminDtos.CreateAdminRequest;
import com.example.ccbilling.dto.PageResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.AdministratorRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountService {

    private final AdministratorRepository administrators;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AdminAccountService(AdministratorRepository administrators, PasswordEncoder passwordEncoder, AuditService audit) {
        this.administrators = administrators;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional
    public AdminResponse create(CreateAdminRequest request) {
        if (administrators.existsByUsername(request.username())) {
            throw ApiException.conflict("USERNAME_TAKEN", "Administrator username already exists");
        }
        Administrator saved = administrators.save(new Administrator(request.username(), request.fullName().trim(),
                passwordEncoder.encode(request.password())));
        audit.record(AuditAction.ADMIN_CREATED, "Administrator", saved.getId(), "username=" + saved.getUsername());
        return AdminResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminResponse> list(Pageable pageable) {
        return PageResponse.of(administrators.findAll(pageable), AdminResponse::from);
    }

    @Transactional
    public AdminResponse setEnabled(Long adminId, boolean enabled, Long actingAdminId) {
        Administrator admin = administrators.findById(adminId).orElseThrow(() -> ApiException.notFound("Administrator"));
        if (!enabled && admin.getId().equals(actingAdminId)) {
            throw ApiException.unprocessable("CANNOT_DISABLE_SELF", "Administrators cannot disable their own account");
        }
        admin.setEnabled(enabled);
        audit.record(AuditAction.ADMIN_ENABLED_CHANGED, "Administrator", adminId, "enabled=" + enabled);
        return AdminResponse.from(admin);
    }
}
