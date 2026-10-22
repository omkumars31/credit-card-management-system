package com.example.ccbilling.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ccbilling.domain.CardStatus;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CardNumbersTest {

    @Test
    void generatedNumbersStartWithBinHave16DigitsAndPassLuhn() {
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            String pan = CardNumbers.generate("411111", random);
            assertEquals(16, pan.length());
            assertTrue(pan.startsWith("411111"));
            assertTrue(CardNumbers.isLuhnValid(pan), pan);
        }
    }

    @Test
    void luhnKnownVectors() {
        assertTrue(CardNumbers.isLuhnValid("4111111111111111"));
        assertTrue(CardNumbers.isLuhnValid("5500005555555559"));
        assertFalse(CardNumbers.isLuhnValid("4111111111111112"));
        assertFalse(CardNumbers.isLuhnValid("abc"));
        assertEquals(1, CardNumbers.luhnCheckDigit("411111111111111"));
    }

    @Test
    void maskKeepsOnlyFirstSixAndLastFourDigits() {
        assertEquals("411111******1111", CardNumbers.mask("4111111111111111"));
        assertEquals("1111", CardNumbers.last4("4111111111111111"));
        assertFalse(CardNumbers.mask("4111111111111111").contains("1111111111"));
    }

    @Test
    void invalidBinIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CardNumbers.generate("12345", new Random()));
        assertThrows(IllegalArgumentException.class, () -> CardNumbers.generate("12345a", new Random()));
    }

    @Test
    void hashIsStable64HexAndDiffersPerNumber() {
        String h1 = CardNumbers.sha256("4111111111111111");
        assertEquals(h1, CardNumbers.sha256("4111111111111111"));
        assertEquals(64, h1.length());
        assertNotEquals(h1, CardNumbers.sha256("4111111111111129"));
        assertFalse(h1.contains("4111111111111111"));
    }

    @Test
    void cardStatusTransitions() {
        assertTrue(CardStatus.ACTIVE.canTransitionTo(CardStatus.BLOCKED));
        assertTrue(CardStatus.BLOCKED.canTransitionTo(CardStatus.ACTIVE));
        assertTrue(CardStatus.ACTIVE.canTransitionTo(CardStatus.CLOSED));
        assertTrue(CardStatus.BLOCKED.canTransitionTo(CardStatus.CLOSED));
        assertFalse(CardStatus.ACTIVE.canTransitionTo(CardStatus.ACTIVE));
        assertFalse(CardStatus.CLOSED.canTransitionTo(CardStatus.ACTIVE));
        assertFalse(CardStatus.CLOSED.canTransitionTo(CardStatus.BLOCKED));
    }
}
