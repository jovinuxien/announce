package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** The payout reached the seller. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record OrderSettled(String payoutRef,
                           LocalDateTime at) {
}
