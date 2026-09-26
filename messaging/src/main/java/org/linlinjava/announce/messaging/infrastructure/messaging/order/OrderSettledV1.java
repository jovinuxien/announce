package org.linlinjava.announce.messaging.infrastructure.messaging.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/** The payout reached the seller; the deal is complete. */
@JsonIgnoreProperties(ignoreUnknown = true)
@PublicEvent(type = "order.settled", version = 1)
public record OrderSettledV1(String orderId, String announceId, Integer buyerId, Integer sellerId, long payoutCents, String currency,
                             LocalDateTime settledAt) {
}
