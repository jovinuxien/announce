package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The carrier reported the parcel delivered. */
@PublicEvent(type = "shipment.delivered", version = 1)
public record ShipmentDeliveredV1(String orderId,
                                  String carrier,
                                  String trackingNumber,
                                  LocalDateTime at) {
}
