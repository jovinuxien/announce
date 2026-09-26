package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** The payment provider confirmed the payment; the seller must ship within the ship window. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record OrderPaid(String paymentId,
                        LocalDateTime at) {
}
