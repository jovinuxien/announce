package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** The deal must be unwound after payment; the payment context refunds the buyer. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record RefundRequested(long amountCents,
                              String currency,
                              String reason,
                              LocalDateTime at) {
}
