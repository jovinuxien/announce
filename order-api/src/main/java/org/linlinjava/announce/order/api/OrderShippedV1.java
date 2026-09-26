package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The carrier reported the parcel dispatched. */
@PublicEvent(type = "order.shipped", version = 1)
public record OrderShippedV1(String orderId,
                             String announceId,
                             Integer buyerId,
                             Integer sellerId,
                             String shipmentId,
                             String trackingNumber,
                             LocalDateTime at) {
}
