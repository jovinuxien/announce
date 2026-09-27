package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The carrier reported the parcel on its way. */
@PublicEvent(type = "shipment.in-transit", version = 1)
public record ShipmentInTransitV1(String orderId,
                                  String carrier,
                                  String trackingNumber,
                                  LocalDateTime at) {
}
