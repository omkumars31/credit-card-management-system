package com.example.ccbilling.support;

import com.example.ccbilling.billing.BillingCalculator;
import com.example.ccbilling.billing.BillingRules;
import com.example.ccbilling.billing.StatementFigures;
import com.example.ccbilling.domain.BillingEntry;
import com.example.ccbilling.domain.BinConfiguration;
import com.example.ccbilling.domain.CreditCard;
import com.example.ccbilling.domain.Customer;
import com.example.ccbilling.domain.EntryType;
import com.example.ccbilling.domain.PaymentMethod;
import com.example.ccbilling.domain.PaymentMethodType;
import com.example.ccbilling.domain.Statement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/** Deterministic fixtures. Ids are assigned by reflection because there is no database in unit tests. */
public final class TestData {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 20, 12, 0);

    private TestData() {
    }

    public static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** cycle day 15, due 20 days, min due 5% (floor 200), finance 3.5%, GST 18%, late fee 10% (100..1000, threshold 500). */
    public static BillingRules rules() {
        return new BillingRules(15, 20, bd("5"), bd("200"), bd("3.5"), bd("18"), bd("10"), bd("100"), bd("1000"), bd("500"));
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static Customer customer(long id) {
        return withId(new Customer("Test Customer " + id, "customer" + id + "@example.com", null, "{hash}"), id);
    }

    public static BinConfiguration bin(long id) {
        return withId(new BinConfiguration("41111" + id, "Test BIN", rules()), id);
    }

    public static CreditCard card(long id, Customer customer, BinConfiguration bin, LocalDate openedOn) {
        return withId(new CreditCard(customer, bin, "411111******1111", "1111", "hash-" + id, bd("100000.00"),
                openedOn, openedOn.plusYears(5)), id);
    }

    public static PaymentMethod method(long id, Customer customer) {
        return withId(new PaymentMethod(customer, PaymentMethodType.UPI, "My UPI", "*****-bank"), id);
    }

    public static BillingEntry entry(CreditCard card, String ref, EntryType type, String amount, LocalDate date) {
        return new BillingEntry(card, ref, type, bd(amount), "test " + type, date, NOW, "ADMIN:test");
    }

    public static Statement statement(long id, CreditCard card, LocalDate periodEnd, StatementFigures figures) {
        BinConfiguration bin = card.getBinConfiguration();
        return withId(new Statement(card, bin, bin.toRules(), bin.getRuleVersion(), periodEnd.minusMonths(1).plusDays(1),
                periodEnd, periodEnd.plusDays(bin.toRules().paymentDueDays()), NOW, "ADMIN:test", figures), id);
    }

    /** Statement with the given total due and the minimum due produced by the BIN rules. */
    public static Statement statement(long id, CreditCard card, LocalDate periodEnd, String totalDue) {
        BillingRules r = card.getBinConfiguration().toRules();
        StatementFigures f = BillingCalculator.computeStatement(BigDecimal.ZERO, bd(totalDue), BigDecimal.ZERO,
                BigDecimal.ZERO, r);
        return statement(id, card, periodEnd, f);
    }

    public static StatementFigures figures(String total, String minimum) {
        return new StatementFigures(bd("0.00"), bd(total), bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd(total), bd(minimum));
    }

    public static void setId(Object entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
