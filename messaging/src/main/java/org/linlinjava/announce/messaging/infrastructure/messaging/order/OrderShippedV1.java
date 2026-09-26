package org.linlinjava.announce.messaging.infrastructure.messaging.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/** The carrier reported the parcel dispatched. */
@JsonIgnoreProperties(ignoreUnknown = true)
@PublicEvent(type = "order.shipped", version = 1)
public record OrderShippedV1(String orderId, String announceId, Integer buyerId, Integer sellerId, String carrier, String trackingNumber,
                             LocalDateTime shippedAt) {
}
