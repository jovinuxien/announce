package org.linlinjava.announce.messaging.api;

import org.linlinjava.announce.cqrs.messaging.PublicEvent;

import java.time.LocalDateTime;

/**
 * A conversation was opened. For a LISTING conversation the buyer and seller are set and the id is stable for the
 * pair (announce, buyer); for a SUPPORT conversation only the member who asked is known.
 *
 * @param conversationId the conversation id
 * @param type           LISTING or SUPPORT
 * @param announceId     the announce (LISTING) or null
 * @param buyerId        the member who opened a LISTING conversation, or the member asking for support
 * @param sellerId       the owner of the announce (LISTING) or null
 * @param openedAt       when it was opened
 */
@PublicEvent(type = "messaging.conversation-opened", version = 1)
public record ConversationOpenedV1(String conversationId,
                                   String type,
                                   String announceId,
                                   Integer buyerId,
                                   Integer sellerId,
                                   LocalDateTime openedAt) {
}
