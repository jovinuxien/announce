package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** The payment context opened an intent; the buyer can pay. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record OrderPaymentIntentCreated(String paymentId,
                                        LocalDateTime at) {
}
