package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The carrier reported the parcel delivered; the dispute window starts. */
@PublicEvent(type = "order.delivered", version = 1)
public record OrderDeliveredV1(String orderId,
                               String announceId,
                               Integer buyerId,
                               Integer sellerId,
                               LocalDateTime at) {
}
