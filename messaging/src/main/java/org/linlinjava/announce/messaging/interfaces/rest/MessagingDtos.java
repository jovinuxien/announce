package org.linlinjava.announce.messaging.interfaces.rest;

import org.linlinjava.announce.messaging.domain.conversation.Conversation;
import org.linlinjava.announce.messaging.domain.conversation.Offer;
import org.linlinjava.announce.messaging.domain.conversation.Participant;

import java.time.LocalDateTime;
import java.util.List;

/** Request and response bodies of the messaging REST API. */
public final class MessagingDtos {

    private MessagingDtos() {
    }

    /** {@code type} LISTING needs {@code announceId}; SUPPORT takes an optional {@code subject}. */
    public record OpenConversationRequest(String type, String announceId, String subject) {
    }

    /** Same shape as the WebSocket {@code post} / {@code offer} frames. */
    public record PostMessageRequest(String clientMsgId, String contentType, String content, String attachmentUrl, Long amountCents, String currency) {
    }

    public record ReadRequest(int seq) {
    }

    public record BlockRequest(String reason) {
    }

    public record BulletinRequest(String audience, String clientType, String content, LocalDateTime expiresAt) {
    }

    public record PageResponse<T>(List<T> items, int page, int size) {
    }

    /** The state a command produced, without the aggregate's internal bookkeeping. */
    public record ConversationResponse(String id, String type, String announceId, Integer buyerId, Integer sellerId, String subject,
                                       String status, int lastSeq, List<Participant> participants, Offer openOffer, Offer acceptedOffer,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static ConversationResponse of(Conversation c) {
            return new ConversationResponse(c.id(), c.type().name(), c.announceId(), c.buyerId(), c.sellerId(), c.subject(), c.status().name(),
                    c.lastSeq(), c.participants(), c.openOffer(), c.acceptedOffer(), c.createdAt(), c.updatedAt());
        }
    }
}
