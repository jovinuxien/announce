package org.linlinjava.announce.shipping.domain.events;

import java.time.LocalDateTime;

/** The deal ended before dispatch. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record ShipmentCancelled(String reason,
                                LocalDateTime at) {
}
