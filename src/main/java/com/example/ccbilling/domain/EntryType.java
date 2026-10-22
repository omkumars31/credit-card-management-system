package com.example.ccbilling.domain;

/** Kind of billing entry. REFUND is a credit; everything else is a debit. */
public enum EntryType {
    PURCHASE(true), FEE(true), LATE_FEE(true), REFUND(false);

    private final boolean debit;

    EntryType(boolean debit) { this.debit = debit; }

    public boolean isDebit() { return debit; }
}
