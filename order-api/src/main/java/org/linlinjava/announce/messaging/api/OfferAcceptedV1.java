package org.linlinjava.announce.messaging.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;
import java.time.LocalDateTime;

/** The seller accepted a buyer's OFFER in a listing conversation (published by the messaging module on {@code messaging.v1}; this copy is the stub the order module compiles against until {@code messaging-api} ships; field names and the type string are the contract). */
@PublicEvent(type = "conversation.offer-accepted", version = 1)
public record OfferAcceptedV1(String conversationId,
                              String announceId,
                              Integer buyerId,
                              Integer sellerId,
                              long amountCents,
                              String currency,
                              LocalDateTime acceptedAt) {
}
