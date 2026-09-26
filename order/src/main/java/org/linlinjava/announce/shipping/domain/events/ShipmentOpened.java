package org.linlinjava.announce.shipping.domain.events;

import java.time.LocalDateTime;

/** The deal was paid: the seller may book the shipment. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record ShipmentOpened(Integer sellerId,
                             Integer buyerId,
                             LocalDateTime at) {
}
