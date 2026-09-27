package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/**
 * The payment provider confirmed the buyer's payment for a deal. announce-management marks the announce SOLD;
 * messaging posts a system message; the seller is told to ship. No money has moved to the seller yet.
 */
@PublicEvent(type = "order.paid", version = 1)
public record OrderPaidV1(String orderId,
                          String announceId,
                          Integer buyerId,
                          Integer sellerId,
                          long amountCents,
                          String currency,
                          LocalDateTime paidAt) {
}
