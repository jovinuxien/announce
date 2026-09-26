package org.linlinjava.announce.messaging.domain.conversation.events;

import java.time.LocalDateTime;

/** The seller declined the latest open offer. */
public record OfferDeclined(String messageId, LocalDateTime at) {
}
