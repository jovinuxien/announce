package org.linlinjava.announce.shipping.domain.events;

import java.time.LocalDateTime;

/** The carrier reported the parcel on its way. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record ShipmentInTransit(String carrierEventId,
                                String carrier,
                                String trackingNumber,
                                LocalDateTime at) {
}
