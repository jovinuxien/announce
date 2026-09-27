package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The provider holds an intent for the deal; the buyer completes it on the client with {@code clientSecret}. */
@PublicEvent(type = "payment.intent-created", version = 1)
public record PaymentIntentCreatedV1(String orderId,
                                     String paymentId,
                                     String provider,
                                     String intentRef,
                                     String clientSecret,
                                     long amountCents,
                                     String currency,
                                     LocalDateTime at) {
}
