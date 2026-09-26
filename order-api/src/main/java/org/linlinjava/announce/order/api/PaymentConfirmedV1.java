package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The provider confirmed the buyer paid (webhook). The order becomes PAID. */
@PublicEvent(type = "payment.confirmed", version = 1)
public record PaymentConfirmedV1(String orderId,
                                 String paymentId,
                                 String provider,
                                 long amountCents,
                                 String currency,
                                 LocalDateTime at) {
}
