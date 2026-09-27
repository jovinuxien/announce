package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/**
 * The buyer's payment was refunded after the deal had been paid (ship window expired, or a dispute resolved as
 * refund). announce-management puts the announce back to PUBLISHED.
 */
@PublicEvent(type = "order.refunded", version = 1)
public record OrderRefundedV1(String orderId,
                              String announceId,
                              Integer buyerId,
                              Integer sellerId,
                              long amountCents,
                              String currency,
                              String reason,
                              LocalDateTime refundedAt) {
}
