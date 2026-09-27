package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The carrier scanned the parcel in. */
@PublicEvent(type = "shipment.dispatched", version = 1)
public record ShipmentDispatchedV1(String orderId,
                                   String carrier,
                                   String trackingNumber,
                                   LocalDateTime at) {
}
