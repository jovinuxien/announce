package org.linlinjava.announce.shipping.domain.events;

import java.time.LocalDateTime;

/** The carrier reported the parcel lost, returned or undeliverable. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record ShipmentFailed(String carrierEventId,
                             String carrier,
                             String trackingNumber,
                             String reason,
                             LocalDateTime at) {
}
