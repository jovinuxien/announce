package org.linlinjava.announce.goods.domain.announce.events;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.LocalDateTime;

/**
 * The announce was taken down; it can be published again. Either the owner withdrew it ({@code moderatedBy} and
 * {@code reason} null) or staff did through {@code ModerateWithdraw}, in which case the moderator's admin id and the
 * reason are recorded. Events written before moderation existed deserialise with both fields null.
 * Top-level class: EventSourcingDB rejects the {@code $} of a nested class name.
 */
public record AnnounceWithdrawn(LocalDateTime at, Integer moderatedBy, String reason) {

    @JsonCreator
    public AnnounceWithdrawn {
    }

    /** Withdrawn by the owner. */
    public AnnounceWithdrawn(LocalDateTime at) {
        this(at, null, null);
    }

    public boolean isModerated() {
        return moderatedBy != null;
    }
}
