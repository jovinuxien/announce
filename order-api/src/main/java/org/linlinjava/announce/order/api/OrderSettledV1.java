package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The payout reached the seller: the deal is complete. */
@PublicEvent(type = "order.settled", version = 1)
public record OrderSettledV1(String orderId,
                             String announceId,
                             Integer buyerId,
                             Integer sellerId,
                             long payoutCents,
                             String currency,
                             LocalDateTime settledAt) {
}
