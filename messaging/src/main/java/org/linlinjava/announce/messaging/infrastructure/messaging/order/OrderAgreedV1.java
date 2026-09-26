package org.linlinjava.announce.messaging.infrastructure.messaging.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/** A deal was agreed (auction won, purchase accepted, or chat offer accepted); the buyer now has to pay. */
@JsonIgnoreProperties(ignoreUnknown = true)
@PublicEvent(type = "order.agreed", version = 1)
public record OrderAgreedV1(String orderId, String announceId, Integer buyerId, Integer sellerId, String door, String conversationId,
                            long amountCents, String currency, LocalDateTime agreedAt) {
}
