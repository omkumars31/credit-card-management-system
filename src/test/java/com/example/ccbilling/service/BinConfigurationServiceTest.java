package com.example.ccbilling.service;

import static com.example.ccbilling.support.TestData.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ccbilling.domain.BinConfiguration;
import com.example.ccbilling.dto.BinDtos.BinConfigRequest;
import com.example.ccbilling.dto.BinDtos.BinConfigResponse;
import com.example.ccbilling.exception.ApiException;
import com.example.ccbilling.repository.BinConfigurationRepository;
import com.example.ccbilling.support.TestData;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BinConfigurationServiceTest {

    @Mock BinConfigurationRepository bins;
    @Mock AuditService audit;

    BinConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new BinConfigurationService(bins, audit);
        when(bins.save(any(BinConfiguration.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static BinConfigRequest request(String bin, int cycleDay, int dueDays, String floor, String cap, String gst) {
        return new BinConfigRequest(bin, "Gold", cycleDay, dueDays, bd("5"), bd("200"), bd("3.5"), bd(gst),
                bd("10"), bd(floor), bd(cap), bd("500"));
    }

    @Test
    void validConfigurationIsCreatedAtRuleVersionOne() {
        BinConfigResponse r = service.create(request("412345", 10, 20, "100", "1000", "18"));
        assertEquals("412345", r.binCode());
        assertEquals(1, r.ruleVersion());
        assertEquals(10, r.rules().billingCycleDay());
    }

    @Test
    void duplicateBinIsRejected() {
        when(bins.existsByBinCode("412345")).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class, () -> service.create(request("412345", 10, 20, "100", "1000", "18")));
        assertEquals("BIN_ALREADY_EXISTS", ex.getCode());
        verify(bins, never()).save(any(BinConfiguration.class));
    }

    @Test
    void conflictingRulesAreRejectedWithAllViolationsListed() {
        ApiException ex = assertThrows(ApiException.class, () -> service.create(request("412345", 10, 20, "500", "100", "18")));
        assertEquals("INVALID_BILLING_RULES", ex.getCode());
        assertTrue(ex.getMessage().contains("lateFeeCap"));

        ApiException ex2 = assertThrows(ApiException.class, () -> service.create(request("412345", 10, 28, "100", "1000", "18")));
        assertTrue(ex2.getMessage().contains("paymentDueDays"));
    }

    @Test
    void changingARuleBumpsTheVersionButRenamingDoesNot() {
        BinConfiguration existing = TestData.bin(1);
        when(bins.findById(1L)).thenReturn(Optional.of(existing));

        BinConfigResponse renamed = service.update(1L, new BinConfigRequest("411111", "Renamed", 15, 20, bd("5.0000"),
                bd("200.00"), bd("3.5000"), bd("18.0000"), bd("10"), bd("100"), bd("1000"), bd("500")));
        assertEquals("Renamed", renamed.name());
        assertEquals(1, renamed.ruleVersion()); // same values at a different scale are not a change

        BinConfigResponse changed = service.update(1L, request("411111", 15, 20, "100", "1000", "28"));
        assertEquals(2, changed.ruleVersion());
        assertEquals(0, new BigDecimal("28").compareTo(changed.rules().gstRate()));
    }

    @Test
    void binCodeOfAnExistingConfigurationCannotChange() {
        when(bins.findById(1L)).thenReturn(Optional.of(TestData.bin(1)));
        ApiException ex = assertThrows(ApiException.class, () -> service.update(1L, request("999999", 15, 20, "100", "1000", "18")));
        assertEquals("BIN_CODE_IMMUTABLE", ex.getCode());
    }

    @Test
    void updatingUnknownConfigurationIsNotFound() {
        assertEquals("NOT_FOUND", assertThrows(ApiException.class,
                () -> service.update(404L, request("411111", 15, 20, "100", "1000", "18"))).getCode());
    }
}
