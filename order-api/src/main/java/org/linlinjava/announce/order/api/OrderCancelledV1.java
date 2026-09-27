package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/**
 * The deal ended before payment (a party cancelled, or the pay window expired). {@code wasPaid} is always false
 * here; announce-management republishes the announce only when it had marked it sold.
 */
@PublicEvent(type = "order.cancelled", version = 1)
public record OrderCancelledV1(String orderId,
                               String announceId,
                               Integer buyerId,
                               Integer sellerId,
                               String reason,
                               LocalDateTime cancelledAt) {
}
