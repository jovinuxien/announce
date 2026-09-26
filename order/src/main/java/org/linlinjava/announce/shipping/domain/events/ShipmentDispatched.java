package org.linlinjava.announce.shipping.domain.events;

import java.time.LocalDateTime;

/** The carrier scanned the parcel in. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record ShipmentDispatched(String carrierEventId,
                                 String carrier,
                                 String trackingNumber,
                                 LocalDateTime at) {
}
