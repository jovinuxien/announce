package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The provider transferred the payout to the seller (webhook). The order settles. */
@PublicEvent(type = "payment.payout-sent", version = 1)
public record PayoutSentV1(String orderId,
                           String paymentId,
                           Integer sellerId,
                           long payoutCents,
                           String currency,
                           String payoutRef,
                           LocalDateTime at) {
}
