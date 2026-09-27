package org.linlinjava.announce.payment.domain.events;

import java.time.LocalDateTime;

/** The provider confirmed the buyer paid (webhook). Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record PaymentConfirmed(String providerEventId,
                               LocalDateTime at) {
}
