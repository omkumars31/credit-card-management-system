package com.example.ccbilling.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/**
 * One realistic life cycle through the real HTTP layer, security and database:
 * card -> purchases/refund -> statement -> partial payment -> late fee -> second statement (finance charge, GST,
 * late fee carried) -> payment -> reversal, with customer isolation and audit checks along the way.
 * BIN rules: cycle day 15, due 20 days, min due 5% (floor 200), finance 3.5%/month, GST 18%,
 * late fee 10% of unpaid minimum (floor 100, cap 1000, threshold 500).
 */
class BillingWorkflowIntegrationTest extends IntegrationTestBase {

    private void assertDec(String expected, JsonNode actual) {
        assertEquals(0, dec(expected).compareTo(actual.decimalValue()), "expected " + expected + " but was " + actual);
    }

    private ResultActions pay(MockHttpSession s, String key, long statementId, long methodId, String amount) throws Exception {
        return mvc.perform(post("/api/customers/me/payments").session(s).with(csrf()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("statementId", statementId, "paymentMethodId", methodId, "amount", dec(amount))));
    }

    private String entry(String type, String amount, String date, String ref) throws Exception {
        return json("entryType", type, "amount", dec(amount), "description", "Test " + type, "transactionDate", date,
                "referenceId", ref);
    }

    @Test
    void fullBillingLifecycle() throws Exception {
        clock.setDate(LocalDate.of(2026, 1, 10));
        MockHttpSession admin = adminSession();

        // --- BIN configuration (administrator) ---
        String binCode = String.valueOf(900000 + ThreadLocalRandom.current().nextInt(99000));
        long binId = read(postAs(admin, "/api/admin/bin-configurations", json(
                "binCode", binCode, "name", "Lifecycle BIN", "billingCycleDay", 15, "paymentDueDays", 20,
                "minDuePercentage", dec("5"), "minDueFloor", dec("200"), "financeChargeMonthlyRate", dec("3.5"),
                "gstRate", dec("18"), "lateFeePercentage", dec("10"), "lateFeeFloor", dec("100"),
                "lateFeeCap", dec("1000"), "lateFeeThreshold", dec("500")))
                .andExpect(status().isCreated())).get("id").asLong();

        // conflicting rules are rejected
        postAs(admin, "/api/admin/bin-configurations", json(
                "binCode", "123456", "name", "Bad", "billingCycleDay", 15, "paymentDueDays", 20,
                "minDuePercentage", dec("5"), "minDueFloor", dec("200"), "financeChargeMonthlyRate", dec("3.5"),
                "gstRate", dec("18"), "lateFeePercentage", dec("10"), "lateFeeFloor", dec("500"),
                "lateFeeCap", dec("100"), "lateFeeThreshold", dec("500")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_BILLING_RULES"));

        // --- customers ---
        String emailA = uniqueEmail("alice");
        String emailB = uniqueEmail("bob");
        long customerA = register(emailA);
        register(emailB);
        MockHttpSession alice = login("CUSTOMER", emailA, CUSTOMER_PASSWORD);
        MockHttpSession bob = login("CUSTOMER", emailB, CUSTOMER_PASSWORD);

        // --- card: only the masked number is ever returned ---
        ResultActions issued = postAs(admin, "/api/admin/cards", json("customerId", customerA, "binConfigurationId", binId,
                "creditLimit", dec("100000.00"))).andExpect(status().isCreated());
        String issuedBody = raw(issued);
        JsonNode card = mapper.readTree(issuedBody);
        long cardId = card.get("id").asLong();
        String masked = card.get("maskedNumber").asText();
        assertTrue(masked.matches("^" + binCode + "\\*{6}\\d{4}$"), masked);
        assertFalse(issuedBody.contains("numberHash") || issuedBody.contains("cardNumber"));

        // --- payment methods (masked identifiers only) ---
        String methodBody = raw(postAs(alice, "/api/customers/me/payment-methods", json("type", "UPI",
                "displayName", "Alice UPI", "identifier", "alice@okbank")).andExpect(status().isCreated()));
        assertFalse(methodBody.contains("alice@okbank"));
        long methodA = mapper.readTree(methodBody).get("id").asLong();
        long methodB = read(postAs(bob, "/api/customers/me/payment-methods", json("type", "NET_BANKING",
                "displayName", "Bob bank", "identifier", "00112233445566")).andExpect(status().isCreated())).get("id").asLong();

        // --- billing entries (administrator feed) ---
        clock.setDate(LocalDate.of(2026, 3, 20));
        String entriesUrl = "/api/admin/cards/" + cardId + "/entries";
        postAs(admin, entriesUrl, entry("PURCHASE", "10000.00", "2026-02-20", "p1")).andExpect(status().isCreated());
        postAs(admin, entriesUrl, entry("PURCHASE", "2500.50", "2026-03-01", "p2")).andExpect(status().isCreated());
        postAs(admin, entriesUrl, entry("REFUND", "500.50", "2026-03-05", "r1")).andExpect(status().isCreated());
        postAs(admin, entriesUrl, entry("PURCHASE", "10.00", "2026-03-01", "p1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REFERENCE"));
        postAs(admin, entriesUrl, entry("PURCHASE", "200000.00", "2026-03-01", "huge")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_LIMIT_EXCEEDED"));
        postAs(admin, entriesUrl, entry("PURCHASE", "10.00", "2026-03-21", "future")).andExpect(status().isUnprocessableEntity());

        JsonNode balance = read(getAs(admin, "/api/admin/cards/" + cardId + "/balance").andExpect(status().isOk()));
        assertDec("12000.00", balance.get("unbilledNet"));
        assertDec("88000.00", balance.get("availableCredit"));

        // --- statement 1 ---
        String generate = json("cardId", cardId, "periodEnd", "2026-03-15");
        JsonNode s1 = read(postAs(admin, "/api/admin/statements/generate", generate).andExpect(status().isCreated()));
        long stmt1 = s1.get("id").asLong();
        assertEquals("2026-02-16", s1.get("periodStart").asText());
        assertEquals("2026-04-04", s1.get("dueDate").asText());
        assertDec("0.00", s1.get("financeCharge"));
        assertDec("12000.00", s1.get("totalAmountDue"));
        assertDec("600.00", s1.get("minimumAmountDue"));
        assertEquals("UNPAID", s1.get("paymentStatus").asText());
        assertEquals(masked, s1.get("maskedCardNumber").asText());

        // repeated generation never duplicates charges
        postAs(admin, "/api/admin/statements/generate", generate).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATEMENT_ALREADY_EXISTS"));
        getAs(admin, "/api/admin/statements?cardId=" + cardId).andExpect(jsonPath("$.totalElements").value(1));
        // entries were billed: nothing left unbilled
        assertDec("0.00", read(getAs(admin, "/api/admin/cards/" + cardId + "/balance")).get("unbilledNet"));

        // --- customer isolation ---
        getAs(alice, "/api/customers/me/cards").andExpect(jsonPath("$.length()").value(1));
        getAs(bob, "/api/customers/me/cards").andExpect(jsonPath("$.length()").value(0));
        getAs(bob, "/api/customers/me/cards/" + cardId).andExpect(status().isNotFound());
        getAs(bob, "/api/customers/me/cards/" + cardId + "/balance").andExpect(status().isNotFound());
        getAs(bob, "/api/customers/me/cards/" + cardId + "/entries").andExpect(status().isNotFound());
        getAs(bob, "/api/customers/me/statements/" + stmt1).andExpect(status().isNotFound());
        getAs(bob, "/api/customers/me/statements").andExpect(jsonPath("$.totalElements").value(0));
        pay(bob, "bob-pay-00001", stmt1, methodB, "100.00").andExpect(status().isNotFound());
        pay(alice, "alice-pay-bobs-method", stmt1, methodB, "100.00").andExpect(status().isNotFound());
        getAs(alice, "/api/customers/me/statements/" + stmt1).andExpect(status().isOk());

        // --- partial payment + idempotency ---
        ResultActions first = pay(alice, "alice-pay-0001", stmt1, methodA, "300.00").andExpect(status().isCreated());
        JsonNode pay1 = read(first);
        long payment1 = pay1.get("id").asLong();
        assertEquals("COMPLETED", pay1.get("status").asText());

        JsonNode afterPay = read(getAs(alice, "/api/customers/me/statements/" + stmt1));
        assertDec("300.00", afterPay.get("paidAmount"));
        assertDec("11700.00", afterPay.get("outstandingAmount"));
        assertEquals("PARTIALLY_PAID", afterPay.get("paymentStatus").asText());

        JsonNode replay = read(pay(alice, "alice-pay-0001", stmt1, methodA, "300.00").andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true")));
        assertEquals(payment1, replay.get("id").asLong());
        assertDec("300.00", read(getAs(alice, "/api/customers/me/statements/" + stmt1)).get("paidAmount")); // not applied twice

        pay(alice, "alice-pay-0001", stmt1, methodA, "301.00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        pay(alice, "alice-pay-0002", stmt1, methodA, "20000.00").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_OUTSTANDING"));
        mvc.perform(post("/api/customers/me/payments").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("statementId", stmt1, "paymentMethodId", methodA, "amount", dec("10.00"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
        pay(alice, "alice-pay-0003", stmt1, methodA, "10.005").andExpect(status().isBadRequest());
        assertDec("300.00", read(getAs(alice, "/api/customers/me/statements/" + stmt1)).get("paidAmount"));

        // --- late fee ---
        postAs(admin, "/api/admin/statements/" + stmt1 + "/late-fee", "{}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_OVERDUE"));
        clock.setDate(LocalDate.of(2026, 4, 10));
        JsonNode fee = read(postAs(admin, "/api/admin/statements/" + stmt1 + "/late-fee", "{}").andExpect(status().isCreated()));
        assertDec("300.00", fee.get("unpaidMinimumDue"));
        assertDec("100.00", fee.get("feeAmount"));
        assertDec("18.00", fee.get("gstAmount"));
        assertDec("118.00", fee.get("totalAmount"));
        assertEquals("POSTED", fee.get("status").asText());
        assertTrue(fee.get("ruleSnapshot").asText().contains("ruleVersion=1"));
        postAs(admin, "/api/admin/statements/" + stmt1 + "/late-fee", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LATE_FEE_ALREADY_ASSESSED"));
        getAs(alice, "/api/customers/me/late-fees").andExpect(jsonPath("$.totalElements").value(1));
        getAs(bob, "/api/customers/me/late-fees").andExpect(jsonPath("$.totalElements").value(0));
        getAs(admin, "/api/admin/late-fees?cardId=" + cardId).andExpect(jsonPath("$.totalElements").value(1));

        // --- statement 2: carried balance, finance charge, GST and late fee ---
        postAs(admin, entriesUrl, entry("PURCHASE", "1000.00", "2026-04-10", "p4")).andExpect(status().isCreated());
        clock.setDate(LocalDate.of(2026, 4, 20));
        postAs(admin, "/api/admin/statements/generate", json("cardId", cardId, "periodEnd", "2026-04-14"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_PERIOD_END"));
        JsonNode s2 = read(postAs(admin, "/api/admin/statements/generate", json("cardId", cardId, "periodEnd", "2026-04-15"))
                .andExpect(status().isCreated()));
        long stmt2 = s2.get("id").asLong();
        assertEquals("2026-03-16", s2.get("periodStart").asText());
        assertEquals("2026-05-05", s2.get("dueDate").asText());
        assertDec("11700.00", s2.get("previousBalance"));
        assertDec("1000.00", s2.get("purchasesTotal"));
        assertDec("118.00", s2.get("feesTotal"));
        assertDec("409.50", s2.get("financeCharge"));
        assertDec("73.71", s2.get("gstOnFinanceCharge"));
        assertDec("13301.21", s2.get("totalAmountDue"));
        assertDec("665.06", s2.get("minimumAmountDue"));

        // the first statement keeps its historical values
        JsonNode s1Again = read(getAs(alice, "/api/customers/me/statements/" + stmt1));
        assertDec("12000.00", s1Again.get("totalAmountDue"));
        assertDec("600.00", s1Again.get("minimumAmountDue"));

        // --- superseded statement, payment and reversal ---
        pay(alice, "alice-pay-0004", stmt1, methodA, "100.00").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STATEMENT_SUPERSEDED"));
        JsonNode pay2 = read(pay(alice, "alice-pay-0005", stmt2, methodA, "665.06").andExpect(status().isCreated()));
        long payment2 = pay2.get("id").asLong();
        getAs(alice, "/api/customers/me/payments").andExpect(jsonPath("$.totalElements").value(2));
        getAs(bob, "/api/customers/me/payments").andExpect(jsonPath("$.totalElements").value(0));
        getAs(bob, "/api/customers/me/payments/" + payment2).andExpect(status().isNotFound());

        postAs(alice, "/api/admin/payments/" + payment2 + "/reverse", json("reason", "x")).andExpect(status().isForbidden());
        JsonNode reversed = read(postAs(admin, "/api/admin/payments/" + payment2 + "/reverse", json("reason", "Bounced cheque"))
                .andExpect(status().isOk()));
        assertEquals("REVERSED", reversed.get("status").asText());
        JsonNode s2After = read(getAs(alice, "/api/customers/me/statements/" + stmt2));
        assertDec("0.00", s2After.get("paidAmount"));
        assertEquals("UNPAID", s2After.get("paymentStatus").asText());
        postAs(admin, "/api/admin/payments/" + payment2 + "/reverse", json("reason", "again")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_REVERSED"));
        postAs(admin, "/api/admin/payments/" + payment1 + "/reverse", json("reason", "old")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REVERSAL_NOT_ALLOWED"));

        // --- audit trail ---
        getAs(alice, "/api/admin/audit-logs").andExpect(status().isForbidden());
        getAs(admin, "/api/admin/audit-logs?action=PAYMENT_COMPLETED&size=100").andExpect(status().isOk());
        assertTrue(read(getAs(admin, "/api/admin/audit-logs?action=PAYMENT_COMPLETED&size=100")).get("totalElements").asInt() >= 2);
        assertEquals(1, read(getAs(admin, "/api/admin/audit-logs?action=PAYMENT_REVERSED&entityId=" + payment2)).get("totalElements").asInt());
        assertEquals(1, read(getAs(admin, "/api/admin/audit-logs?action=LATE_FEE_ASSESSED&size=100")).get("content").findValues("details").stream()
                .filter(d -> d.asText().contains("statement=" + stmt1)).count());
        JsonNode stmtLogs = read(getAs(admin, "/api/admin/audit-logs?action=STATEMENT_GENERATED&entityType=Statement&size=100"));
        assertTrue(stmtLogs.get("totalElements").asInt() >= 2);

        String allAudit = raw(getAs(admin, "/api/admin/audit-logs?size=100&sort=id,desc").andExpect(status().isOk()));
        assertFalse(allAudit.contains(CUSTOMER_PASSWORD), "passwords must never reach the audit log");
        assertFalse(allAudit.contains(ADMIN_PASSWORD));
        assertFalse(allAudit.contains("alice@okbank"), "payment identifiers must be masked in the audit log");
        assertTrue(allAudit.contains(masked), "card creation is audited with the masked number only");
        assertTrue(allAudit.contains("CARD_CREATED") && allAudit.contains("BIN_CONFIG_CREATED"));

        // --- card lifecycle rules ---
        patchAs(admin, "/api/admin/cards/" + cardId + "/status", json("status", "CLOSED")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CARD_HAS_BALANCE"));
        patchAs(admin, "/api/admin/cards/" + cardId + "/status", json("status", "BLOCKED")).andExpect(status().isOk());
        postAs(admin, entriesUrl, entry("PURCHASE", "10.00", "2026-04-18", "blocked")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CARD_NOT_ACTIVE"));
        patchAs(admin, "/api/admin/cards/" + cardId + "/status", json("status", "ACTIVE")).andExpect(status().isOk());
    }

    @Test
    void binRuleChangesBumpTheVersionAndNeverTouchExistingStatements() throws Exception {
        clock.setDate(LocalDate.of(2026, 1, 10));
        MockHttpSession admin = adminSession();
        String binCode = String.valueOf(800000 + ThreadLocalRandom.current().nextInt(99000));
        BigDecimal gst18 = dec("18");
        long binId = read(postAs(admin, "/api/admin/bin-configurations", ruleBody(binCode, gst18))
                .andExpect(status().isCreated())).get("id").asLong();
        assertEquals(1, read(getAs(admin, "/api/admin/bin-configurations/" + binId)).get("ruleVersion").asInt());

        putAs(admin, "/api/admin/bin-configurations/" + binId, ruleBody(binCode, gst18)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleVersion").value(1));
        putAs(admin, "/api/admin/bin-configurations/" + binId, ruleBody(binCode, dec("28"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleVersion").value(2));
        putAs(admin, "/api/admin/bin-configurations/" + binId, ruleBody("111111", dec("28"))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BIN_CODE_IMMUTABLE"));
        postAs(admin, "/api/admin/bin-configurations", ruleBody(binCode, gst18)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIN_ALREADY_EXISTS"));
        assertEquals(1, read(getAs(admin, "/api/admin/audit-logs?action=BIN_CONFIG_UPDATED&entityId=" + binId + "&size=1"))
                .get("content").size());
    }

    private String ruleBody(String binCode, BigDecimal gst) throws Exception {
        return json("binCode", binCode, "name", "Rules BIN", "billingCycleDay", 15, "paymentDueDays", 20,
                "minDuePercentage", dec("5"), "minDueFloor", dec("200"), "financeChargeMonthlyRate", dec("3.5"),
                "gstRate", gst, "lateFeePercentage", dec("10"), "lateFeeFloor", dec("100"),
                "lateFeeCap", dec("1000"), "lateFeeThreshold", dec("500"));
    }
}
