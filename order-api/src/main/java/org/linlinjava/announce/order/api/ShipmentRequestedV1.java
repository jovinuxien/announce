package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The seller booked the shipment; the label and tracking number exist. */
@PublicEvent(type = "shipment.requested", version = 1)
public record ShipmentRequestedV1(String orderId,
                                  Integer sellerId,
                                  Integer buyerId,
                                  String carrier,
                                  String service,
                                  String trackingNumber,
                                  String labelUrl,
                                  LocalDateTime at) {
}
