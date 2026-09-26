package org.linlinjava.announce.messaging.infrastructure.messaging.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/** The payment provider confirmed the buyer's payment; the seller has to ship. */
@JsonIgnoreProperties(ignoreUnknown = true)
@PublicEvent(type = "order.paid", version = 1)
public record OrderPaidV1(String orderId, String announceId, Integer buyerId, Integer sellerId, long amountCents, String currency,
                          LocalDateTime paidAt) {
}
