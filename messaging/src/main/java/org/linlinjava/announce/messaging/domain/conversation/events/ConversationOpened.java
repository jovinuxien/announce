package org.linlinjava.announce.messaging.domain.conversation.events;

import org.linlinjava.announce.messaging.domain.conversation.ConversationStatus;
import org.linlinjava.announce.messaging.domain.conversation.ConversationType;
import org.linlinjava.announce.messaging.domain.conversation.Participant;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A conversation was opened with its initial participants: buyer and seller (LISTING, status OPEN) or the member
 * asking for support (SUPPORT, status WAITING).
 */
public record ConversationOpened(ConversationType type,
                                 String announceId,
                                 Integer buyerId,
                                 Integer sellerId,
                                 String subject,
                                 ConversationStatus status,
                                 Participant createdBy,
                                 List<Participant> participants,
                                 LocalDateTime at) {
}
