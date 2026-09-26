package org.linlinjava.announce.payment.domain.events;

import java.time.LocalDateTime;

/** The provider refunded the buyer (webhook). Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record RefundSent(String providerEventId,
                         String refundRef,
                         LocalDateTime at) {
}
