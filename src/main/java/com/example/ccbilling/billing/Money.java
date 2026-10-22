package com.example.ccbilling.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money conventions used everywhere in the application:
 * currency is INR (assumption), scale is 2, rounding is HALF_UP and is applied once per derived amount.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    /** base * percentage / 100, rounded once to 2 decimals (HALF_UP). The multiplication is exact. */
    public static BigDecimal percentOf(BigDecimal base, BigDecimal percentage) {
        return base.multiply(percentage).divide(HUNDRED, SCALE, ROUNDING);
    }

    /** True when the value has at most two decimal places (ignoring trailing zeros). */
    public static boolean hasAtMostTwoDecimals(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= SCALE;
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
