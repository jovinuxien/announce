package org.linlinjava.announce.messaging.domain.conversation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationBlocked;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationClosed;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationOpened;
import org.linlinjava.announce.messaging.domain.conversation.events.MessagePosted;
import org.linlinjava.announce.messaging.domain.conversation.events.OfferAccepted;
import org.linlinjava.announce.messaging.domain.conversation.events.OfferDeclined;
import org.linlinjava.announce.messaging.domain.conversation.events.ParticipantJoined;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * State of the Conversation aggregate under {@code /conversation/{id}}, rebuilt from its events. Immutable; the
 * {@code apply} methods are the state rebuilding functions and the rules that guard each command live in
 * {@link ConversationRules}.
 *
 * <p>The state keeps only what the rules need: the participants, the last message seq, the client message ids
 * already seen (idempotent posting), the latest open offer and the accepted one. Message bodies live in the read
 * model.
 */
public record Conversation(String id,
                           ConversationType type,
                           String announceId,
                           Integer buyerId,
                           Integer sellerId,
                           String subject,
                           ConversationStatus status,
                           List<Participant> participants,
                           int lastSeq,
                           Map<String, MessageRef> messagesByClientId,
                           Offer openOffer,
                           Offer acceptedOffer,
                           LocalDateTime createdAt,
                           LocalDateTime updatedAt) {

    public Conversation {
        participants = participants == null ? List.of() : List.copyOf(participants);
        messagesByClientId = messagesByClientId == null ? Map.of() : Map.copyOf(messagesByClientId);
    }

    @JsonIgnore
    public boolean writable() {
        return status.writable();
    }

    public Optional<Participant> participant(ParticipantType participantType, Integer participantId) {
        return participants.stream().filter(p -> p.matches(participantType, participantId)).findFirst();
    }

    /** Whether the sender takes part; SYSTEM always does. */
    public boolean hasParticipant(Sender sender) {
        return sender.isSystem() || participant(sender.participantType(), sender.id()).isPresent();
    }

    public Optional<MessageRef> messageByClientId(String clientMsgId) {
        return clientMsgId == null ? Optional.empty() : Optional.ofNullable(messagesByClientId.get(clientMsgId));
    }

    // ------------------------------------------------------------------ state rebuilding

    public static Conversation apply(String id, ConversationOpened e) {
        return new Conversation(id, e.type(), e.announceId(), e.buyerId(), e.sellerId(), e.subject(), e.status(),
                e.participants(), 0, Map.of(), null, null, e.at(), e.at());
    }

    public Conversation apply(ParticipantJoined e) {
        List<Participant> joined = new ArrayList<>(participants);
        if (participant(e.participant().type(), e.participant().id()).isEmpty()) {
            joined.add(e.participant());
        }
        return new Conversation(id, type, announceId, buyerId, sellerId, subject, e.status(), joined, lastSeq,
                messagesByClientId, openOffer, acceptedOffer, createdAt, e.at());
    }

    public Conversation apply(MessagePosted e) {
        Map<String, MessageRef> seen = new LinkedHashMap<>(messagesByClientId);
        if (e.clientMsgId() != null) {
            seen.put(e.clientMsgId(), new MessageRef(e.messageId(), e.seq()));
        }
        Offer open = e.contentType() == ContentType.OFFER ? new Offer(e.messageId(), e.offerAmountCents(), e.currency()) : openOffer;
        return new Conversation(id, type, announceId, buyerId, sellerId, subject, status, participants, Math.max(lastSeq, e.seq()),
                seen, open, acceptedOffer, createdAt, e.at());
    }

    public Conversation apply(OfferAccepted e) {
        return new Conversation(id, type, announceId, buyerId, sellerId, subject, status, participants, lastSeq,
                messagesByClientId, null, new Offer(e.messageId(), e.amountCents(), e.currency()), createdAt, e.at());
    }

    public Conversation apply(OfferDeclined e) {
        return new Conversation(id, type, announceId, buyerId, sellerId, subject, status, participants, lastSeq,
                messagesByClientId, null, acceptedOffer, createdAt, e.at());
    }

    public Conversation apply(ConversationClosed e) {
        return new Conversation(id, type, announceId, buyerId, sellerId, subject, ConversationStatus.CLOSED, participants, lastSeq,
                messagesByClientId, openOffer, acceptedOffer, createdAt, e.at());
    }

    public Conversation apply(ConversationBlocked e) {
        return new Conversation(id, type, announceId, buyerId, sellerId, subject, ConversationStatus.BLOCKED, participants, lastSeq,
                messagesByClientId, openOffer, acceptedOffer, createdAt, e.at());
    }
}
