package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The carrier reported the parcel lost, returned or undeliverable; staff take over through a dispute. */
@PublicEvent(type = "shipment.failed", version = 1)
public record ShipmentFailedV1(String orderId,
                               String carrier,
                               String trackingNumber,
                               String reason,
                               LocalDateTime at) {
}
