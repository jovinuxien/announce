package org.linlinjava.announce.payment.domain.events;

import java.time.LocalDateTime;

/** The order context asked for a refund and the provider was asked to send it. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record RefundRequested(long amountCents,
                              String refundRef,
                              String reason,
                              LocalDateTime at) {
}
