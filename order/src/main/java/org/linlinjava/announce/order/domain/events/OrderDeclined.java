package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** The seller declined a purchase request. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record OrderDeclined(Integer sellerId,
                            String reason,
                            LocalDateTime at) {
}
