package org.linlinjava.announce.messaging.application;

import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.db.dao.reactive.AnnounceChatMessageMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationParticipantMapper;
import org.linlinjava.announce.db.domain.AnnounceChatMessage;
import org.linlinjava.announce.db.domain.AnnounceConversation;
import org.linlinjava.announce.db.domain.AnnounceConversationExample;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipant;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipantExample;
import org.linlinjava.announce.messaging.domain.conversation.ConversationRules;
import org.linlinjava.announce.messaging.domain.conversation.ConversationStatus;
import org.linlinjava.announce.messaging.domain.conversation.ParticipantType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads over the messaging read model (never the event store). A member sees the conversations they take part
 * in; staff see every conversation.
 */
@Service
public class ConversationQueries {

    public static final String CONVERSATION_NOT_FOUND = "conversation-not-found";

    /** Who is asking: a member or a staff member. */
    public record Viewer(ParticipantType type, Integer id) {
        public boolean staff() {
            return type == ParticipantType.ADMIN;
        }
    }

    public record ParticipantView(ParticipantType type, Integer id, String role, int lastReadSeq, LocalDateTime joinedAt) {
    }

    public record OfferView(String messageId, BigDecimal amount, String currency, LocalDateTime at) {
    }

    public record ConversationView(String id, String type, String announceId, Integer buyerId, Integer sellerId, String subject,
                                   String status, String lastMessageId, int lastMessageSeq, LocalDateTime lastMessageAt, int messageCount,
                                   Integer unread, OfferView openOffer, OfferView acceptedOffer, List<ParticipantView> participants,
                                   LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime closedAt, String blockedReason) {
    }

    public record MessageView(String id, String conversationId, int seq, String senderType, Integer senderId, String contentType,
                              String content, String attachmentUrl, BigDecimal offerAmount, String offerCurrency, String offerStatus,
                              String clientMsgId, LocalDateTime sentAt) {
        public static MessageView of(AnnounceChatMessage m) {
            return new MessageView(m.getId(), m.getConversationId(), m.getSeq(), m.getSenderType(), m.getSenderId(), m.getContentType(),
                    m.getContent(), m.getAttachmentUrl(), m.getOfferAmount(), m.getOfferCurrency(), m.getOfferStatus(), m.getClientMsgId(),
                    m.getSentTime());
        }
    }

    private final AnnounceConversationMapper conversations;
    private final AnnounceConversationParticipantMapper participants;
    private final AnnounceChatMessageMapper messages;
    private final MessagingProperties properties;

    public ConversationQueries(AnnounceConversationMapper conversations, AnnounceConversationParticipantMapper participants,
                               AnnounceChatMessageMapper messages, MessagingProperties properties) {
        this.conversations = conversations;
        this.participants = participants;
        this.messages = messages;
        this.properties = properties;
    }

    /** The viewer's conversations, most recent activity first; {@code page} is 1-based. */
    public Flux<ConversationView> inbox(Viewer viewer, int page, int size) {
        int limit = Math.max(1, Math.min(size, properties.getMaxPageSize()));
        long offset = (long) Math.max(1, page) * limit - limit;
        return conversations.selectByParticipant(viewer.type().name(), viewer.id(), offset, limit).concatMap(row -> view(row, viewer));
    }

    /** One conversation; 404 when unknown, 403 when the viewer is neither a participant nor staff. */
    public Mono<ConversationView> view(String id, Viewer viewer) {
        return conversations.selectByPrimaryKey(id)
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.NOT_FOUND, CONVERSATION_NOT_FOUND, "Conversation " + id + " does not exist")))
                .flatMap(row -> view(row, viewer))
                .filter(view -> viewer.staff() || view.unread() != null)
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.FORBIDDEN, ConversationRules.NOT_PARTICIPANT, "Not a participant of conversation " + id)));
    }

    /** Messages after {@code afterSeq}, oldest first, for a participant (or staff). */
    public Flux<MessageView> messages(String id, Viewer viewer, int afterSeq, int limit) {
        int bounded = Math.max(1, Math.min(limit, properties.getMaxPageSize()));
        return view(id, viewer).flatMapMany(v -> messages.selectAfter(id, Math.max(0, afterSeq), bounded)).map(MessageView::of);
    }

    /** Whether the viewer may attach to the conversation: a participant, or any staff member. */
    public Mono<Boolean> mayAttach(String id, Viewer viewer) {
        return view(id, viewer).map(v -> true).onErrorReturn(CommandRejectedException.class, false);
    }

    /** The support queue (status WAITING) or any other status, oldest first; staff only. */
    public Flux<ConversationView> byStatus(ConversationStatus status, int page, int size) {
        int limit = Math.max(1, Math.min(size, properties.getMaxPageSize()));
        long offset = (long) Math.max(1, page) * limit - limit;
        AnnounceConversationExample example = new AnnounceConversationExample();
        example.or().andStatusEqualTo(status.name()).andDeletedEqualTo(false);
        example.setOrderByClause("add_time asc, id asc");
        Viewer staff = new Viewer(ParticipantType.ADMIN, null);
        return conversations.selectPageByExample(example, offset, limit).concatMap(row -> view(row, staff));
    }

    private Mono<ConversationView> view(AnnounceConversation row, Viewer viewer) {
        AnnounceConversationParticipantExample example = new AnnounceConversationParticipantExample();
        example.or().andConversationIdEqualTo(row.getId()).andDeletedEqualTo(false);
        example.setOrderByClause("id asc");
        return participants.selectByExample(example).collectList().map(rows -> {
            Integer unread = rows.stream().filter(p -> viewer.id() != null && p.getParticipantType().equals(viewer.type().name()) && p.getParticipantId().equals(viewer.id()))
                    .findFirst().map(p -> Math.max(0, n(row.getLastMessageSeq()) - n(p.getLastReadSeq()))).orElse(null);
            List<ParticipantView> views = rows.stream().map(ConversationQueries::participant).toList();
            OfferView open = row.getOpenOfferMessageId() == null ? null
                    : new OfferView(row.getOpenOfferMessageId(), row.getOpenOfferAmount(), row.getOpenOfferCurrency(), null);
            OfferView accepted = row.getAcceptedOfferAmount() == null ? null
                    : new OfferView(null, row.getAcceptedOfferAmount(), row.getAcceptedOfferCurrency(), row.getAcceptedOfferTime());
            return new ConversationView(row.getId(), row.getType(), row.getAnnounceId(), row.getBuyerId(), row.getSellerId(), row.getSubject(),
                    row.getStatus(), row.getLastMessageId(), n(row.getLastMessageSeq()), row.getLastMessageTime(), n(row.getMessageCount()),
                    unread, open, accepted, views, row.getAddTime(), row.getUpdateTime(), row.getClosedTime(), row.getBlockedReason());
        });
    }

    private static ParticipantView participant(AnnounceConversationParticipant p) {
        return new ParticipantView(ParticipantType.valueOf(p.getParticipantType()), p.getParticipantId(), p.getRole(), n(p.getLastReadSeq()), p.getJoinedTime());
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }
}
