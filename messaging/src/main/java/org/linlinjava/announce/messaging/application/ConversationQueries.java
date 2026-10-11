package org.linlinjava.announce.messaging.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import org.linlinjava.announce.messaging.infrastructure.metrics.MessagingMeters;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Reads over the messaging read model (never the event store). A member sees the conversations they take part
 * in; staff see every conversation.
 *
 * <p>A page of conversations costs two statements whatever its size: the page itself, then the participants of
 * every conversation on it in one {@code IN} query (tuning step 7 of {@code doc/messaging-architecture.md}).
 * Every public read is timed as {@code messaging.query.latency{query}}.
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
    private final MeterRegistry meters;
    private final MessagingProperties properties;

    public ConversationQueries(AnnounceConversationMapper conversations, AnnounceConversationParticipantMapper participants,
                               AnnounceChatMessageMapper messages, MeterRegistry meters, MessagingProperties properties) {
        this.conversations = conversations;
        this.participants = participants;
        this.messages = messages;
        this.meters = meters;
        this.properties = properties;
    }

    /** The viewer's conversations, most recent activity first; {@code page} is 1-based. */
    public Flux<ConversationView> inbox(Viewer viewer, int page, int size) {
        int limit = Math.max(1, Math.min(size, properties.getMaxPageSize()));
        long offset = (long) Math.max(1, page) * limit - limit;
        return timed("inbox", withParticipants(conversations.selectByParticipant(viewer.type().name(), viewer.id(), offset, limit), viewer));
    }

    /** One conversation; 404 when unknown, 403 when the viewer is neither a participant nor staff. */
    public Mono<ConversationView> view(String id, Viewer viewer) {
        return timed("conversation", lookup(id, viewer));
    }

    /** Messages after {@code afterSeq}, oldest first, for a participant (or staff). */
    public Flux<MessageView> messages(String id, Viewer viewer, int afterSeq, int limit) {
        int bounded = Math.max(1, Math.min(limit, properties.getMaxPageSize()));
        return timed("messages", lookup(id, viewer)
                .flatMapMany(v -> messages.selectAfter(id, Math.max(0, afterSeq), bounded)).map(MessageView::of));
    }

    private Mono<ConversationView> lookup(String id, Viewer viewer) {
        return conversations.selectByPrimaryKey(id)
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.NOT_FOUND, CONVERSATION_NOT_FOUND, "Conversation " + id + " does not exist")))
                .flatMap(row -> view(row, viewer))
                .filter(view -> viewer.staff() || view.unread() != null)
                .switchIfEmpty(Mono.error(() -> new CommandRejectedException(HttpStatus.FORBIDDEN, ConversationRules.NOT_PARTICIPANT, "Not a participant of conversation " + id)));
    }

    /**
     * Whether the viewer may attach to the conversation: a participant, or any staff member.
     *
     * <p>The read model trails the command side: a socket opened right after the conversation was opened may ask
     * before the projection wrote the row. A conversation that is not there (yet) is therefore looked up again,
     * {@code messaging.attach.rechecks} times, {@code messaging.attach.recheck-interval} apart, before the answer
     * is no. A conversation that is there answers at once: its row and its first participants are written in one
     * transaction, members never join later, and staff may attach to any conversation.
     */
    public Mono<Boolean> mayAttach(String id, Viewer viewer) {
        return mayAttach(id, viewer, Math.max(0, properties.getAttach().getRechecks()));
    }

    private Mono<Boolean> mayAttach(String id, Viewer viewer, int rechecksLeft) {
        return Mono.defer(() -> conversations.selectByPrimaryKey(id))
                .flatMap(row -> view(row, viewer))
                .map(view -> viewer.staff() || view.unread() != null)
                .switchIfEmpty(Mono.defer(() -> rechecksLeft == 0 ? Mono.just(false)
                        : Mono.delay(properties.getAttach().getRecheckInterval()).then(mayAttach(id, viewer, rechecksLeft - 1))));
    }

    /** The support queue (status WAITING) or any other status, oldest first; staff only. */
    public Flux<ConversationView> byStatus(ConversationStatus status, int page, int size) {
        int limit = Math.max(1, Math.min(size, properties.getMaxPageSize()));
        long offset = (long) Math.max(1, page) * limit - limit;
        AnnounceConversationExample example = new AnnounceConversationExample();
        example.or().andStatusEqualTo(status.name()).andDeletedEqualTo(false);
        example.setOrderByClause("add_time asc, id asc");
        Viewer staff = new Viewer(ParticipantType.ADMIN, null);
        return timed("by_status", withParticipants(conversations.selectPageByExample(example, offset, limit), staff));
    }

    /** The views of a page of conversations, in page order, with the participants of the whole page read in one query. */
    private Flux<ConversationView> withParticipants(Flux<AnnounceConversation> page, Viewer viewer) {
        return page.collectList().flatMapMany(rows -> {
            if (rows.isEmpty()) {
                return Flux.empty();
            }
            AnnounceConversationParticipantExample example = new AnnounceConversationParticipantExample();
            example.or().andConversationIdIn(rows.stream().map(AnnounceConversation::getId).toList()).andDeletedEqualTo(false);
            example.setOrderByClause("conversation_id asc, id asc");
            return participants.selectByExample(example).collectMultimap(AnnounceConversationParticipant::getConversationId)
                    .flatMapMany(byConversation -> Flux.fromIterable(rows).map(row -> view(row, participantsOf(byConversation, row), viewer)));
        });
    }

    private static List<AnnounceConversationParticipant> participantsOf(Map<String, Collection<AnnounceConversationParticipant>> byConversation,
                                                                        AnnounceConversation row) {
        Collection<AnnounceConversationParticipant> rows = byConversation.get(row.getId());
        return rows == null ? List.of() : new ArrayList<>(rows);
    }

    /** The view of one conversation; its participants in one query. */
    private Mono<ConversationView> view(AnnounceConversation row, Viewer viewer) {
        AnnounceConversationParticipantExample example = new AnnounceConversationParticipantExample();
        example.or().andConversationIdEqualTo(row.getId()).andDeletedEqualTo(false);
        example.setOrderByClause("id asc");
        return participants.selectByExample(example).collectList().map(rows -> view(row, rows, viewer));
    }

    /** The view of one conversation given its row and its participant rows; {@code unread} is null unless the viewer is one of them. */
    static ConversationView view(AnnounceConversation row, List<AnnounceConversationParticipant> rows, Viewer viewer) {
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
    }

    /** {@code messaging.query.latency}: from the subscription to the complete answer (or the error). */
    private <T> Flux<T> timed(String query, Flux<T> answer) {
        return Flux.defer(() -> {
            Timer.Sample sample = Timer.start(meters);
            return answer.doOnComplete(() -> sample.stop(MessagingMeters.queryLatency(meters, query, MessagingMeters.OUTCOME_OK)))
                    .doOnError(e -> sample.stop(MessagingMeters.queryLatency(meters, query, outcome(e))));
        });
    }

    private <T> Mono<T> timed(String query, Mono<T> answer) {
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meters);
            return answer.doOnSuccess(v -> sample.stop(MessagingMeters.queryLatency(meters, query, MessagingMeters.OUTCOME_OK)))
                    .doOnError(e -> sample.stop(MessagingMeters.queryLatency(meters, query, outcome(e))));
        });
    }

    private static String outcome(Throwable e) {
        return e instanceof CommandRejectedException ? MessagingMeters.OUTCOME_REJECTED : MessagingMeters.OUTCOME_ERROR;
    }

    private static ParticipantView participant(AnnounceConversationParticipant p) {
        return new ParticipantView(ParticipantType.valueOf(p.getParticipantType()), p.getParticipantId(), p.getRole(), n(p.getLastReadSeq()), p.getJoinedTime());
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }
}
