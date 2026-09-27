package org.linlinjava.announce.order.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** Buyer and seller agreed on a price through one of the three doors; the payment context creates the payment intent. */
@PublicEvent(type = "order.agreed", version = 1)
public record OrderAgreedV1(String orderId,
                            String announceId,
                            Integer buyerId,
                            Integer sellerId,
                            String door,
                            long priceCents,
                            long feeCents,
                            long payoutCents,
                            String currency,
                            String title,
                            LocalDateTime agreedAt) {
}
