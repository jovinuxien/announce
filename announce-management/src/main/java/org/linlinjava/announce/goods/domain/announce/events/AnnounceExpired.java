package org.linlinjava.announce.goods.domain.announce.events;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.LocalDateTime;

/**
 * The announce ended; it can be published again. Either the validity period ran out (scheduler, {@code moderatedBy}
 * and {@code reason} null) or staff ended it early through {@code ModerateExpire}. Top-level class: EventSourcingDB
 * rejects the {@code $} of a nested class name.
 */
public record AnnounceExpired(LocalDateTime at, Integer moderatedBy, String reason) {

    @JsonCreator
    public AnnounceExpired {
    }

    /** Expired by the scheduler because the validity period ended. */
    public AnnounceExpired(LocalDateTime at) {
        this(at, null, null);
    }

    public boolean isModerated() {
        return moderatedBy != null;
    }
}
