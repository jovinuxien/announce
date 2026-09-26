package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** The carrier reported the parcel dispatched. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record OrderShipped(String shipmentId,
                           String trackingNumber,
                           LocalDateTime at) {
}
