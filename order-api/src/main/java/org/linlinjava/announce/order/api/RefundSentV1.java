package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The provider refunded the buyer (webhook). The order becomes REFUNDED. */
@PublicEvent(type = "payment.refund-sent", version = 1)
public record RefundSentV1(String orderId,
                           String paymentId,
                           Integer buyerId,
                           long amountCents,
                           String currency,
                           String refundRef,
                           String reason,
                           LocalDateTime at) {
}
