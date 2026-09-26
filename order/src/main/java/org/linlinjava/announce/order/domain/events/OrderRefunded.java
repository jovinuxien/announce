package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** The buyer got the money back. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record OrderRefunded(String refundRef,
                            LocalDateTime at) {
}
