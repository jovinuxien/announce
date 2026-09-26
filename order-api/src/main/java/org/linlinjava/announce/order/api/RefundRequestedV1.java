package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The deal must be unwound after payment (ship window expired, dispute resolved as refund); the payment context refunds the buyer. */
@PublicEvent(type = "order.refund-requested", version = 1)
public record RefundRequestedV1(String orderId,
                                Integer buyerId,
                                long amountCents,
                                String currency,
                                String reason,
                                LocalDateTime at) {
}
