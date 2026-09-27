package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** A dispute blocks the payout until staff resolve it. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record DisputeOpened(Integer openedBy,
                            String openedByType,
                            String reason,
                            LocalDateTime at) {
}
