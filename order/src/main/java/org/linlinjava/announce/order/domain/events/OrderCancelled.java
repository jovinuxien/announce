package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** The deal ended before payment. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record OrderCancelled(Integer actorId,
                             String actorType,
                             String reason,
                             LocalDateTime at) {
}
