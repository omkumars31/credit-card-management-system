package com.example.ccbilling.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BillingRulesValidatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static BillingRules valid() {
        return new BillingRules(15, 20, bd("5"), bd("200"), bd("3.5"), bd("18"), bd("10"), bd("100"), bd("1000"), bd("500"));
    }

    @Test
    void validRulesHaveNoViolations() {
        assertTrue(BillingRulesValidator.violations(valid()).isEmpty());
    }

    @Test
    void cycleDayMustBeBetween1And28() {
        BillingRules r = new BillingRules(29, 20, bd("5"), bd("200"), bd("3.5"), bd("18"), bd("10"), bd("100"), bd("1000"), bd("500"));
        List<String> v = BillingRulesValidator.violations(r);
        assertEquals(1, v.size());
        assertTrue(v.get(0).contains("billingCycleDay"));
        assertEquals(1, BillingRulesValidator.violations(
                new BillingRules(0, 20, bd("5"), bd("200"), bd("3.5"), bd("18"), bd("10"), bd("100"), bd("1000"), bd("500"))).size());
        assertTrue(BillingRulesValidator.violations(
                new BillingRules(28, 20, bd("5"), bd("200"), bd("3.5"), bd("18"), bd("10"), bd("100"), bd("1000"), bd("500"))).isEmpty());
    }

    @Test
    void dueDateMustFallBeforeNextStatement() {
        BillingRules r = new BillingRules(15, 28, bd("5"), bd("200"), bd("3.5"), bd("18"), bd("10"), bd("100"), bd("1000"), bd("500"));
        assertTrue(BillingRulesValidator.violations(r).get(0).contains("paymentDueDays"));
        assertTrue(BillingRulesValidator.violations(
                new BillingRules(15, 27, bd("5"), bd("200"), bd("3.5"), bd("18"), bd("10"), bd("100"), bd("1000"), bd("500"))).isEmpty());
    }

    @Test
    void capLowerThanFloorIsAConflictingRule() {
        BillingRules r = new BillingRules(15, 20, bd("5"), bd("200"), bd("3.5"), bd("18"), bd("10"), bd("500"), bd("100"), bd("500"));
        List<String> v = BillingRulesValidator.violations(r);
        assertEquals(1, v.size());
        assertTrue(v.get(0).contains("lateFeeCap"));
    }

    @Test
    void ratesAndAmountsAreRangeChecked() {
        BillingRules r = new BillingRules(15, 20, bd("0"), bd("-1"), bd("26"), bd("51"), bd("101"), bd("0"), bd("0"), bd("-5"));
        List<String> v = BillingRulesValidator.violations(r);
        assertTrue(v.stream().anyMatch(s -> s.startsWith("minDuePercentage")));
        assertTrue(v.stream().anyMatch(s -> s.startsWith("minDueFloor")));
        assertTrue(v.stream().anyMatch(s -> s.startsWith("financeChargeMonthlyRate")));
        assertTrue(v.stream().anyMatch(s -> s.startsWith("gstRate")));
        assertTrue(v.stream().anyMatch(s -> s.startsWith("lateFeePercentage")));
        assertTrue(v.stream().anyMatch(s -> s.startsWith("lateFeeThreshold")));
    }

    @Test
    void monetaryParametersMayNotHaveMoreThanTwoDecimals() {
        BillingRules r = new BillingRules(15, 20, bd("5"), bd("200.001"), bd("3.5"), bd("18"), bd("10"), bd("100"), bd("1000"), bd("500"));
        assertTrue(BillingRulesValidator.violations(r).get(0).contains("minDueFloor"));
    }

    @Test
    void snapshotContainsEveryParameterAndTheVersion() {
        String s = valid().snapshot(3);
        assertTrue(s.startsWith("ruleVersion=3;"));
        assertTrue(s.contains("cycleDay=15") && s.contains("dueDays=20") && s.contains("gstPct=18")
                && s.contains("lateFeeCap=1000") && s.contains("lateFeeThreshold=500"));
    }
}
