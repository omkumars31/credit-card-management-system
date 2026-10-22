package com.example.ccbilling.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock whose date can be moved forward to simulate billing cycles and due dates. */
public class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    private MutableClock(Instant start, ZoneId zone) {
        this.instant = start;
        this.zone = zone;
    }

    public static MutableClock at(LocalDate date) {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        clock.setDate(date);
        return clock;
    }

    public void setDate(LocalDate date) {
        this.instant = date.atTime(12, 0).toInstant(ZoneOffset.UTC);
    }

    @Override
    public ZoneId getZone() { return zone; }

    @Override
    public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }

    @Override
    public Instant instant() { return instant; }
}
