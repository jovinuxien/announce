package org.linlinjava.announce.shipping.domain.events;

import java.time.LocalDateTime;

/** The seller booked with the carrier; label and tracking number exist. Top-level class: EventSourcingDB rejects the {@code $} of a nested class name. */
public record ShipmentRequested(Integer sellerId,
                                Integer buyerId,
                                String carrier,
                                String service,
                                String parcelSize,
                                String trackingNumber,
                                String labelUrl,
                                LocalDateTime at) {
}
