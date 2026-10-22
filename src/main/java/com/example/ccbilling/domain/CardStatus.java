package com.example.ccbilling.domain;

/** Lifecycle of a credit card. CLOSED is terminal. */
public enum CardStatus {
    ACTIVE, BLOCKED, CLOSED;

    public boolean canTransitionTo(CardStatus target) {
        return switch (this) {
            case ACTIVE -> target == BLOCKED || target == CLOSED;
            case BLOCKED -> target == ACTIVE || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
