package org.linlinjava.announce.orderservice;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/** A clock the tests move forward by hand, so the windows and graces elapse without waiting. */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableClock(Instant start, ZoneId zone) {
        this.now = new AtomicReference<>(start);
        this.zone = zone;
    }

    public void advance(Duration by) {
        now.updateAndGet(i -> i.plus(by));
    }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return new MutableClock(now.get(), zone); }
    @Override public Instant instant() { return now.get(); }
}
