package com.example.ccbilling.billing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;

/**
 * Synthetic card number utilities. The full number is generated, hashed (for uniqueness) and masked;
 * it is never stored or returned.
 */
public final class CardNumbers {

    public static final int LENGTH = 16;

    private CardNumbers() {
    }

    /** BIN (6 digits) + 9 random digits + Luhn check digit. */
    public static String generate(String bin, Random random) {
        if (bin == null || !bin.matches("\\d{6}")) {
            throw new IllegalArgumentException("BIN must be exactly 6 digits");
        }
        StringBuilder sb = new StringBuilder(bin);
        for (int i = 0; i < LENGTH - 7; i++) {
            sb.append(random.nextInt(10));
        }
        sb.append(luhnCheckDigit(sb.toString()));
        return sb.toString();
    }

    public static boolean isLuhnValid(String number) {
        if (number == null || !number.matches("\\d{12,19}")) {
            return false;
        }
        return luhnSum(number, false) % 10 == 0;
    }

    public static int luhnCheckDigit(String payload) {
        int sum = luhnSum(payload, true);
        return (10 - sum % 10) % 10;
    }

    private static int luhnSum(String digits, boolean payloadOnly) {
        int sum = 0;
        boolean doubleIt = payloadOnly;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum;
    }

    /** First 6 and last 4 digits are kept, e.g. 411111******1111. */
    public static String mask(String number) {
        return number.substring(0, 6) + "*".repeat(number.length() - 10) + number.substring(number.length() - 4);
    }

    public static String last4(String number) {
        return number.substring(number.length() - 4);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
