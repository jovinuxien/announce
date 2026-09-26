package org.linlinjava.announce.payment.domain.events;

import java.time.LocalDateTime;

/** The order context released the payout and the provider was asked to transfer it. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record PayoutRequested(long payoutCents,
                              String payoutRef,
                              LocalDateTime at) {
}
