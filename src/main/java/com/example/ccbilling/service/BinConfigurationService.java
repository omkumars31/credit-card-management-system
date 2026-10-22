package com.example.ccbilling.service;

import com.example.ccbilling.billing.BillingRules;
import com.example.ccbilling.billing.BillingRulesValidator;
import com.example.ccbilling.domain.AuditAction;
import com.example.ccbilling.domain.BinConfiguration;
import com.example.ccbilling.dto.BinDtos.BinConfigRequest;
import com.example.ccbilling.dto.BinDtos.BinConfigResponse;
import com.example.ccbilling.dto.PageResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.BinConfigurationRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator-managed BIN configuration. All billing rules live here (nothing is hard-coded in billing code). */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class BinConfigurationService {

    private final BinConfigurationRepository bins;
    private final AuditService audit;

    public BinConfigurationService(BinConfigurationRepository bins, AuditService audit) {
        this.bins = bins;
        this.audit = audit;
    }

    @Transactional
    public BinConfigResponse create(BinConfigRequest request) {
        BillingRules rules = validated(request);
        if (bins.existsByBinCode(request.binCode())) {
            throw ApiException.conflict("BIN_ALREADY_EXISTS", "A configuration for this BIN already exists");
        }
        BinConfiguration saved = bins.save(new BinConfiguration(request.binCode(), request.name().trim(), rules));
        audit.record(AuditAction.BIN_CONFIG_CREATED, "BinConfiguration", saved.getId(),
                "bin=" + saved.getBinCode() + ";" + rules.snapshot(saved.getRuleVersion()));
        return BinConfigResponse.from(saved);
    }

    @Transactional
    public BinConfigResponse update(Long id, BinConfigRequest request) {
        BinConfiguration bin = bins.findById(id).orElseThrow(() -> ApiException.notFound("BIN configuration"));
        if (!bin.getBinCode().equals(request.binCode())) {
            throw ApiException.unprocessable("BIN_CODE_IMMUTABLE", "The BIN code of an existing configuration cannot be changed");
        }
        BillingRules newRules = validated(request);
        BillingRules oldRules = bin.toRules();
        boolean rulesChanged = bin.update(request.name().trim(), newRules);
        audit.record(AuditAction.BIN_CONFIG_UPDATED, "BinConfiguration", id,
                "rulesChanged=" + rulesChanged + ";before=" + oldRules.snapshot(rulesChanged ? bin.getRuleVersion() - 1 : bin.getRuleVersion())
                        + ";after=" + newRules.snapshot(bin.getRuleVersion()));
        return BinConfigResponse.from(bin);
    }

    @Transactional(readOnly = true)
    public BinConfigResponse get(Long id) {
        return BinConfigResponse.from(bins.findById(id).orElseThrow(() -> ApiException.notFound("BIN configuration")));
    }

    @Transactional(readOnly = true)
    public PageResponse<BinConfigResponse> list(Pageable pageable) {
        return PageResponse.of(bins.findAll(pageable), BinConfigResponse::from);
    }

    private BillingRules validated(BinConfigRequest request) {
        BillingRules rules = request.toRules();
        List<String> violations = BillingRulesValidator.violations(rules);
        if (!violations.isEmpty()) {
            throw ApiException.unprocessable("INVALID_BILLING_RULES", String.join("; ", violations));
        }
        return rules;
    }
}
