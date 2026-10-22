package com.example.ccbilling.integration;

import static com.example.ccbilling.support.TestData.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ccbilling.billing.BillingRules;
import com.example.ccbilling.domain.AllocationType;
import com.example.ccbilling.domain.BillingEntry;
import com.example.ccbilling.domain.BinConfiguration;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.Customer;
import com.example.ccbilling.domain.EntryType;
import com.example.ccbilling.domain.LateFeeLedgerEntry;
import com.example.ccbilling.domain.Payment;
import com.example.ccbilling.domain.PaymentAllocation;
import com.example.ccbilling.domain.PaymentMethod;
import com.example.ccbilling.domain.PaymentMethodType;
import com.example.ccbilling.domain.PaymentStatus;
import com.example.ccbilling.domain.Statement;
import com.example.ccbilling.repository.AuditLogRepository;
import com.example.ccbilling.repository.BillingEntryRepository;
import com.example.ccbilling.repository.BinConfigurationRepository;
import com.example.ccbilling.repository.CreditCardRepository;
import com.example.ccbilling.repository.CustomerRepository;
import com.example.ccbilling.repository.LateFeeLedgerRepository;
import com.example.ccbilling.repository.PaymentAllocationRepository;
import com.example.ccbilling.repository.PaymentRepository;
import com.example.ccbilling.repository.StatementRepository;
import com.example.ccbilling.support.TestData;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Repository-level tests against H2 (MODE=MySQL) with the real Flyway migrations. Hibernate runs with
 * ddl-auto=validate, so a successful context start also proves the entity mappings match the migrated schema.
 * H2 is not MySQL: collation, locking details and trigger support differ (see README).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PersistenceIntegrationTest {

    @Autowired TestEntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerRepository customers;
    @Autowired BinConfigurationRepository bins;
    @Autowired CreditCardRepository cards;
    @Autowired StatementRepository statements;
    @Autowired BillingEntryRepository entries;
    @Autowired PaymentRepository payments;
    @Autowired PaymentAllocationRepository allocations;
    @Autowired LateFeeLedgerRepository ledger;

    private static final LocalDate END = LocalDate.of(2026, 3, 15);

    // ---- fixtures ----

    private Customer customer() {
        return em.persist(new Customer("Pat", "pat-" + UUID.randomUUID() + "@example.com", null, "{h}"));
    }

    private BinConfiguration bin() {
        String code = String.valueOf(100000 + (int) (Math.random() * 899999));
        return em.persist(new BinConfiguration(code, "Test", TestData.rules()));
    }

    private CreditCard card(Customer c, BinConfiguration b) {
        return em.persist(new CreditCard(c, b, "411111******1111", "1111", UUID.randomUUID().toString().replace("-", ""),
                bd("100000.00"), LocalDate.of(2026, 1, 10), LocalDate.of(2031, 1, 31)));
    }

    private Statement statement(CreditCard card, LocalDate end) {
        BinConfiguration b = card.getBinConfiguration();
        return em.persist(new Statement(card, b, b.toRules(), b.getRuleVersion(), end.minusMonths(1).plusDays(1), end,
                end.plusDays(20), LocalDateTime.of(2026, 3, 20, 12, 0), "ADMIN:test", TestData.figures("12000.00", "600.00")));
    }

    // ---- schema / migrations ----

    @Test
    void flywayAppliedAllMigrationsAndCreatedEveryTable() {
        Integer applied = jdbc.queryForObject("select count(*) from \"flyway_schema_history\" where \"success\" = true", Integer.class);
        assertTrue(applied != null && applied >= 2, "expected V1 and V2 to be applied");

        Integer tables = jdbc.queryForObject("select count(*) from information_schema.tables where lower(table_name) in "
                + "('customers','administrators','bin_configurations','credit_cards','statements','billing_entries',"
                + "'payment_methods','payments','payment_allocations','late_fee_ledger','audit_logs')", Integer.class);
        assertEquals(11, tables);
    }

    @Test
    void seededBinConfigurationsAreAvailable() {
        BinConfiguration seeded = bins.findByBinCode("411111").orElseThrow();
        assertEquals(15, seeded.toRules().billingCycleDay());
        assertEquals(1, seeded.getRuleVersion());
        assertTrue(bins.findByBinCode("520000").isPresent());
    }

    // ---- constraints ----

    @Test
    void customerEmailMustBeUnique() {
        Customer first = customer();
        assertThrows(DataIntegrityViolationException.class,
                () -> customers.saveAndFlush(new Customer("Other", first.getEmail(), null, "{h}")));
    }

    @Test
    void foreignKeysPreventOrphanedCards() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into credit_cards (customer_id, bin_configuration_id, masked_number, last4, number_hash, credit_limit, "
                        + "status, opened_on, expires_on, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?,?)",
                999999L, 999999L, "411111******1111", "1111", "orphan", 1000, "ACTIVE", LocalDate.now(), LocalDate.now(),
                LocalDateTime.now(), LocalDateTime.now()));
    }

    @Test
    void cardNumberHashMustBeUnique() {
        CreditCard first = card(customer(), bin());
        CreditCard clash = new CreditCard(first.getCustomer(), first.getBinConfiguration(), "411111******2222", "2222",
                first.getNumberHash(), bd("1000.00"), LocalDate.now(), LocalDate.now().plusYears(5));
        assertThrows(DataIntegrityViolationException.class, () -> cards.saveAndFlush(clash));
    }

    @Test
    void creditLimitMustBePositive() {
        Customer c = customer();
        BinConfiguration b = bin();
        CreditCard zeroLimit = new CreditCard(c, b, "411111******3333", "3333", UUID.randomUUID().toString(), bd("0.00"),
                LocalDate.now(), LocalDate.now().plusYears(5));
        assertThrows(DataIntegrityViolationException.class, () -> cards.saveAndFlush(zeroLimit));
    }

    @Test
    void onlyOneStatementPerCardAndPeriod() {
        CreditCard card = card(customer(), bin());
        statement(card, END);
        em.flush();
        BinConfiguration b = card.getBinConfiguration();
        Statement duplicate = new Statement(card, b, b.toRules(), 1, END.minusMonths(1).plusDays(1), END, END.plusDays(20),
                LocalDateTime.now(), "ADMIN:test", TestData.figures("1.00", "1.00"));
        assertThrows(DataIntegrityViolationException.class, () -> statements.saveAndFlush(duplicate));
    }

    @Test
    void lateFeeCanBeAssessedOnlyOncePerStatementEvent() {
        CreditCard card = card(customer(), bin());
        Statement s = statement(card, END);
        BillingEntry fee = em.persist(new BillingEntry(card, "LATEFEE-" + UUID.randomUUID(), EntryType.LATE_FEE, bd("118.00"),
                "fee", END, LocalDateTime.now(), "ADMIN:test"));
        ledger.save(lateFee(s, card, fee));
        em.flush();

        BillingEntry fee2 = em.persist(new BillingEntry(card, "LATEFEE-" + UUID.randomUUID(), EntryType.LATE_FEE, bd("118.00"),
                "fee", END, LocalDateTime.now(), "ADMIN:test"));
        assertThrows(DataIntegrityViolationException.class, () -> ledger.save(lateFee(s, card, fee2)));
    }

    private LateFeeLedgerEntry lateFee(Statement s, CreditCard card, BillingEntry entry) {
        return new LateFeeLedgerEntry(s, card, entry, bd("100.00"), bd("18.0000"), bd("18.00"), bd("300.00"), 1,
                "ruleVersion=1;original", LocalDateTime.of(2026, 4, 10, 12, 0), "ADMIN:test");
    }

    @Test
    void paymentIdempotencyKeyIsUniquePerCustomerAndAmountMustBePositive() {
        Customer c = customer();
        CreditCard card = card(c, bin());
        Statement s = statement(card, END);
        PaymentMethod m = em.persist(new PaymentMethod(c, PaymentMethodType.UPI, "UPI", "****bank"));
        payments.saveAndFlush(new Payment(c, s, m, "key-00000001", "fp", bd("10.00"), LocalDateTime.now()));

        assertThrows(DataIntegrityViolationException.class,
                () -> payments.saveAndFlush(new Payment(c, s, m, "key-00000001", "fp", bd("10.00"), LocalDateTime.now())));
        assertThrows(DataIntegrityViolationException.class,
                () -> payments.saveAndFlush(new Payment(c, s, m, "key-00000002", "fp", bd("0.00"), LocalDateTime.now())));
    }

    // ---- behaviour of mappings and queries ----

    @Test
    void unbilledEntryQueryReturnsOnlyUnassignedEntriesUpToThePeriodEnd() {
        CreditCard card = card(customer(), bin());
        Statement s = statement(card, END);
        BillingEntry inPeriod = em.persist(entry(card, "a", "2026-02-20"));
        em.persist(entry(card, "b", "2026-03-16")); // after the period end
        BillingEntry alreadyBilled = em.persist(entry(card, "c", "2026-02-25"));
        alreadyBilled.assignTo(s);
        em.flush();
        em.clear();

        List<BillingEntry> unbilled = entries.findUnbilledUpTo(card.getId(), END);
        assertEquals(1, unbilled.size());
        assertEquals(inPeriod.getId(), unbilled.get(0).getId());
        assertEquals(2, entries.findByCardIdAndStatementIsNull(card.getId()).size());
    }

    private BillingEntry entry(CreditCard card, String ref, String date) {
        return new BillingEntry(card, ref, EntryType.PURCHASE, bd("10.00"), "x", LocalDate.parse(date), LocalDateTime.now(), "ADMIN:test");
    }

    @Test
    void latestStatementLookupAndRowLockQueriesWork() {
        CreditCard card = card(customer(), bin());
        statement(card, LocalDate.of(2026, 2, 15));
        Statement latest = statement(card, END);
        em.flush();
        em.clear();

        assertEquals(latest.getId(), statements.findTopByCardIdOrderByPeriodEndDesc(card.getId()).orElseThrow().getId());
        assertTrue(statements.existsByCardIdAndPeriodEnd(card.getId(), END));
        assertTrue(statements.findByIdForUpdate(latest.getId()).isPresent());
        assertTrue(cards.findByIdForUpdate(card.getId()).isPresent());
        assertTrue(cards.findByIdAndCustomerId(card.getId(), card.getCustomer().getId()).isPresent());
        assertTrue(cards.findByIdAndCustomerId(card.getId(), -1L).isEmpty());
    }

    @Test
    void statementKeepsItsRuleSnapshotWhenTheBinRulesChangeLater() {
        BinConfiguration b = bin();
        Statement s = statement(card(customer(), b), END);
        em.flush();

        b.update("Test", new BillingRules(15, 20, bd("5"), bd("200"), bd("3.5"), bd("28"), bd("10"), bd("100"), bd("1000"), bd("500")));
        em.flush();
        em.clear();

        Statement reloaded = statements.findById(s.getId()).orElseThrow();
        assertEquals(0, bd("18").compareTo(reloaded.getRules().gstRate()));
        assertEquals(1, reloaded.getRuleVersion());
        assertEquals(2, bins.findById(b.getId()).orElseThrow().getRuleVersion());
    }

    @Test
    void paymentAllocationSumCountsOnlyNonReversedPaymentsCompletedBeforeTheCutoff() {
        Customer c = customer();
        CreditCard card = card(c, bin());
        Statement s = statement(card, END);
        PaymentMethod m = em.persist(new PaymentMethod(c, PaymentMethodType.UPI, "UPI", "****bank"));
        LocalDateTime paidAt = LocalDateTime.of(2026, 4, 3, 12, 0);
        Payment p = payments.saveAndFlush(new Payment(c, s, m, "key-00000009", "fp", bd("300.00"), paidAt));
        allocations.save(new PaymentAllocation(p, s, AllocationType.ALLOCATION, bd("300.00"), paidAt));
        em.flush();

        BigDecimal onTime = allocations.sumValidAllocationsBefore(s.getId(), AllocationType.ALLOCATION,
                PaymentStatus.COMPLETED, LocalDateTime.of(2026, 4, 5, 0, 0));
        assertEquals(0, bd("300.00").compareTo(onTime));
        assertNull(allocations.sumValidAllocationsBefore(s.getId(), AllocationType.ALLOCATION, PaymentStatus.COMPLETED,
                LocalDateTime.of(2026, 4, 3, 0, 0)), "paid after the cut-off");

        p.markReversed(LocalDateTime.of(2026, 4, 6, 12, 0), "bounced", "ADMIN:test");
        em.flush();
        assertNull(allocations.sumValidAllocationsBefore(s.getId(), AllocationType.ALLOCATION, PaymentStatus.COMPLETED,
                LocalDateTime.of(2026, 4, 5, 0, 0)), "reversed payments do not count");
    }

    @Test
    void ledgerRowsAreImmutableEvenIfTheEntityIsModifiedInMemory() {
        CreditCard card = card(customer(), bin());
        Statement s = statement(card, END);
        BillingEntry fee = em.persist(new BillingEntry(card, "LATEFEE-" + UUID.randomUUID(), EntryType.LATE_FEE, bd("118.00"),
                "fee", END, LocalDateTime.now(), "ADMIN:test"));
        LateFeeLedgerEntry row = ledger.save(lateFee(s, card, fee));
        em.flush();

        ReflectionTestUtils.setField(row, "ruleSnapshot", "tampered");
        ReflectionTestUtils.setField(row, "feeAmount", bd("999.00"));
        em.flush();
        em.clear();

        LateFeeLedgerEntry reloaded = ledger.findById(row.getId()).orElseThrow();
        assertEquals("ruleVersion=1;original", reloaded.getRuleSnapshot());
        assertEquals(0, bd("100.00").compareTo(reloaded.getFeeAmount()));
        assertNotEquals("tampered", reloaded.getRuleSnapshot());
        assertNotNull(reloaded.getAssessedAt());
    }

    /**
     * Append-only at the application level: none of the ledger/audit repositories exposes delete or update methods.
     * Limitation: JpaSpecificationExecutor also contains delete(Specification); no application code calls it, and on
     * MySQL the V100 triggers block it at the database level (H2 has no equivalent migration).
     */
    @Test
    void appendOnlyRepositoriesExposeNoDeleteOrUpdateMethods() {
        for (Class<?> repo : List.of(AuditLogRepository.class, LateFeeLedgerRepository.class, PaymentAllocationRepository.class)) {
            for (Method m : repo.getMethods()) {
                boolean specDelete = m.getName().equals("delete") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == Specification.class;
                assertTrue(specDelete || (!m.getName().startsWith("delete") && !m.getName().startsWith("update")),
                        repo.getSimpleName() + " exposes " + m.getName());
            }
        }
    }
}
