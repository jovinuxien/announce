package org.linlinjava.announce.messaging.domain.conversation.events;

import java.time.LocalDateTime;

/** The seller accepted the latest open offer; carries what the order context needs to create the deal. */
public record OfferAccepted(String messageId,
                            String announceId,
                            Integer buyerId,
                            Integer sellerId,
                            long amountCents,
                            String currency,
                            LocalDateTime at) {
}
