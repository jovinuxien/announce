package org.linlinjava.announce.messaging.domain.conversation.events;

import java.time.LocalDateTime;

/** Staff blocked the conversation; nobody can post any more. */
public record ConversationBlocked(Integer staffId, String reason, LocalDateTime at) {
}
