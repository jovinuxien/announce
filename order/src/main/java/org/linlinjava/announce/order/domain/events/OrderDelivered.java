package org.linlinjava.announce.order.domain.events;

import java.time.LocalDateTime;

/** The carrier reported the parcel delivered; the dispute window starts. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record OrderDelivered(LocalDateTime at) {
}
