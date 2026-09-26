package org.linlinjava.announce.messaging.domain.conversation.events;

import org.linlinjava.announce.messaging.domain.conversation.ConversationStatus;
import org.linlinjava.announce.messaging.domain.conversation.Participant;

import java.time.LocalDateTime;

/** A staff member joined; a WAITING support conversation becomes OPEN ({@code status} is the status after the join). */
public record ParticipantJoined(Participant participant, ConversationStatus status, LocalDateTime at) {
}
