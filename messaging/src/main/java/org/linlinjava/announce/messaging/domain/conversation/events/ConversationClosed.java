package org.linlinjava.announce.messaging.domain.conversation.events;

import org.linlinjava.announce.messaging.domain.conversation.Sender;

import java.time.LocalDateTime;

/** A participant or a staff member closed the conversation. */
public record ConversationClosed(Sender by, LocalDateTime at) {
}
