package org.linlinjava.announce.payment.domain.events;

import java.time.LocalDateTime;

/** The provider reported a failed attempt (webhook); the intent stays open. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record PaymentFailed(String providerEventId,
                            String reason,
                            LocalDateTime at) {
}
