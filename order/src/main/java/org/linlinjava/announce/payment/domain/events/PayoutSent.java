package org.linlinjava.announce.payment.domain.events;

import java.time.LocalDateTime;

/** The provider transferred the payout to the seller (webhook). Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record PayoutSent(String providerEventId,
                         String payoutRef,
                         LocalDateTime at) {
}
