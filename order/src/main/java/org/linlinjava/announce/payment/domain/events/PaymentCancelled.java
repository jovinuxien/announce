package org.linlinjava.announce.payment.domain.events;

import java.time.LocalDateTime;

/** The deal was cancelled before payment; the intent is void. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record PaymentCancelled(LocalDateTime at) {
}
