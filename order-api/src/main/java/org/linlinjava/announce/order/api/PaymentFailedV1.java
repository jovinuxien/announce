package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The provider reported the payment attempt failed; the order stays PAYMENT_PENDING. */
@PublicEvent(type = "payment.failed", version = 1)
public record PaymentFailedV1(String orderId,
                              String paymentId,
                              String provider,
                              String reason,
                              LocalDateTime at) {
}
