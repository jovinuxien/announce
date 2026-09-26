package org.linlinjava.announce.messaging.application.live;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/**
 * A change of a conversation to push to its sockets: JOINED, OFFER_ACCEPTED, OFFER_DECLINED, CLOSED, BLOCKED.
 * {@code messageId}, amount and currency are set for the offer outcomes; participant type and id for JOINED.
 */
@PublicEvent(type = "messaging.live.status", version = 1)
public record ConversationStatusLive(String conversationId,
                                     String change,
                                     String status,
                                     String messageId,
                                     Long amountCents,
                                     String currency,
                                     String participantType,
                                     Integer participantId,
                                     LocalDateTime at) {

    public static final String JOINED = "JOINED";
    public static final String OFFER_ACCEPTED = "OFFER_ACCEPTED";
    public static final String OFFER_DECLINED = "OFFER_DECLINED";
    public static final String CLOSED = "CLOSED";
    public static final String BLOCKED = "BLOCKED";
}
