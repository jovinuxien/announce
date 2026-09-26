package org.linlinjava.announce.messaging.infrastructure.projection;

import org.linlinjava.announce.cqrs.command.Subjects;
import org.linlinjava.announce.cqrs.readside.EventEnvelope;
import org.linlinjava.announce.cqrs.readside.ProjectionHandlers;
import org.linlinjava.announce.cqrs.readside.ReactiveProjection;
import org.linlinjava.announce.db.dao.reactive.AnnounceChatMessageMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationParticipantMapper;
import org.linlinjava.announce.db.domain.AnnounceChatMessage;
import org.linlinjava.announce.db.domain.AnnounceConversation;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipant;
import org.linlinjava.announce.messaging.domain.conversation.ContentType;
import org.linlinjava.announce.messaging.domain.conversation.ConversationStatus;
import org.linlinjava.announce.messaging.domain.conversation.Participant;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationBlocked;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationClosed;
import org.linlinjava.announce.messaging.domain.conversation.events.ConversationOpened;
import org.linlinjava.announce.messaging.domain.conversation.events.MessagePosted;
import org.linlinjava.announce.messaging.domain.conversation.events.OfferAccepted;
import org.linlinjava.announce.messaging.domain.conversation.events.OfferDeclined;
import org.linlinjava.announce.messaging.domain.conversation.events.ParticipantJoined;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Writes {@code announce_conversation}, {@code announce_conversation_participant} and {@code announce_chat_message}
 * from the events of {@code /conversation/*}. Group {@code conversation-read-model}; its checkpoint lives in
 * {@code cqrs_readside_progress} next to the tables.
 *
 * <p>At-least-once: every handler is idempotent (rows keyed by the ids and seq numbers the aggregate assigns,
 * transitions repeatable) and records the event id in {@code last_event_id}. The tables come from
 * {@code announce_messaging.sql}, so {@code prepare()} does nothing; {@code reset()} empties them for a rebuild.
 * Read markers ({@code last_read_seq}) are written by the application, not from events, and survive a rebuild
 * only through the participants' own re-reading.
 */
@Component
public class ConversationReadModelProjection implements ReactiveProjection {

    public static final String GROUP = "conversation-read-model";
    public static final String OFFER_OPEN = "OPEN";
    public static final String OFFER_ACCEPTED = "ACCEPTED";
    public static final String OFFER_DECLINED = "DECLINED";
    public static final String OFFER_SUPERSEDED = "SUPERSEDED";

    private final AnnounceConversationMapper conversations;
    private final AnnounceConversationParticipantMapper participants;
    private final AnnounceChatMessageMapper messages;
    private final TransactionalOperator tx;
    private final DatabaseClient db;

    public ConversationReadModelProjection(AnnounceConversationMapper conversations, AnnounceConversationParticipantMapper participants,
                                           AnnounceChatMessageMapper messages, TransactionalOperator tx, DatabaseClient db) {
        this.conversations = conversations;
        this.participants = participants;
        this.messages = messages;
        this.tx = tx;
        this.db = db;
    }

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public void configure(ProjectionHandlers handlers) {
        handlers.on(ConversationOpened.class, (event, envelope) -> {
            String id = idOf(envelope);
            AnnounceConversation row = new AnnounceConversation();
            row.setId(id);
            row.setType(event.type().name());
            row.setAnnounceId(event.announceId());
            row.setBuyerId(event.buyerId());
            row.setSellerId(event.sellerId());
            row.setSubject(event.subject());
            row.setStatus(event.status().name());
            row.setCreatedByType(event.createdBy().type().name());
            row.setCreatedBy(event.createdBy().id());
            row.setLastMessageSeq(0);
            row.setMessageCount(0);
            row.setLastEventId(envelope.eventId());
            row.setAddTime(event.at());
            row.setUpdateTime(event.at());
            row.setDeleted(false);
            return tx.transactional(conversations.upsert(row)
                    .thenMany(Flux.fromIterable(event.participants()).concatMap(p -> participants.upsert(participantRow(id, p, event.at()))))
                    .then());
        });
        handlers.on(ParticipantJoined.class, (event, envelope) -> {
            String id = idOf(envelope);
            AnnounceConversation row = new AnnounceConversation();
            row.setId(id);
            row.setStatus(event.status().name());
            row.setLastEventId(envelope.eventId());
            row.setUpdateTime(event.at());
            return tx.transactional(participants.upsert(participantRow(id, event.participant(), event.at()))
                    .then(conversations.updateByPrimaryKeySelective(row))).then();
        });
        handlers.on(MessagePosted.class, (event, envelope) -> {
            String id = idOf(envelope);
            boolean offer = event.contentType() == ContentType.OFFER;
            AnnounceChatMessage message = new AnnounceChatMessage();
            message.setId(event.messageId());
            message.setConversationId(id);
            message.setSeq(event.seq());
            message.setSenderType(event.sender().type().name());
            message.setSenderId(event.sender().id());
            message.setContentType(event.contentType().name());
            message.setContent(event.content() == null ? "" : event.content());
            message.setAttachmentUrl(event.attachmentUrl());
            message.setOfferAmount(offer ? cents(event.offerAmountCents()) : null);
            message.setOfferCurrency(offer ? event.currency() : null);
            message.setOfferStatus(offer ? OFFER_OPEN : null);
            message.setClientMsgId(event.clientMsgId());
            message.setSentTime(event.at());
            message.setAddTime(event.at());
            message.setUpdateTime(event.at());
            message.setDeleted(false);

            AnnounceConversation counters = new AnnounceConversation();
            counters.setId(id);
            counters.setLastMessageId(event.messageId());
            counters.setLastMessageSeq(event.seq());
            counters.setLastMessageTime(event.at());
            counters.setMessageCount(event.seq());
            counters.setLastEventId(envelope.eventId());
            counters.setUpdateTime(event.at());

            Mono<Void> offers = offer ? conversations.selectByPrimaryKey(id).flatMap(current -> {
                Mono<Integer> supersede = current.getOpenOfferMessageId() == null || current.getOpenOfferMessageId().equals(event.messageId())
                        ? Mono.empty() : messages.updateOfferStatus(current.getOpenOfferMessageId(), OFFER_SUPERSEDED, event.at());
                current.setOpenOfferMessageId(event.messageId());
                current.setOpenOfferAmount(cents(event.offerAmountCents()));
                current.setOpenOfferCurrency(event.currency());
                current.setLastEventId(envelope.eventId());
                current.setUpdateTime(event.at());
                return supersede.then(conversations.updateOffers(current));
            }).then() : Mono.empty();
            return tx.transactional(messages.upsert(message).then(conversations.updateByPrimaryKeySelective(counters)).then(offers)).then();
        });
        handlers.on(OfferAccepted.class, (event, envelope) -> tx.transactional(
                messages.updateOfferStatus(event.messageId(), OFFER_ACCEPTED, event.at())
                        .then(conversations.selectByPrimaryKey(idOf(envelope))).flatMap(current -> {
                            current.setOpenOfferMessageId(null);
                            current.setOpenOfferAmount(null);
                            current.setOpenOfferCurrency(null);
                            current.setAcceptedOfferAmount(cents(event.amountCents()));
                            current.setAcceptedOfferCurrency(event.currency());
                            current.setAcceptedOfferTime(event.at());
                            current.setLastEventId(envelope.eventId());
                            current.setUpdateTime(event.at());
                            return conversations.updateOffers(current);
                        })).then());
        handlers.on(OfferDeclined.class, (event, envelope) -> tx.transactional(
                messages.updateOfferStatus(event.messageId(), OFFER_DECLINED, event.at())
                        .then(conversations.selectByPrimaryKey(idOf(envelope))).flatMap(current -> {
                            current.setOpenOfferMessageId(null);
                            current.setOpenOfferAmount(null);
                            current.setOpenOfferCurrency(null);
                            current.setLastEventId(envelope.eventId());
                            current.setUpdateTime(event.at());
                            return conversations.updateOffers(current);
                        })).then());
        handlers.on(ConversationClosed.class, (event, envelope) -> {
            AnnounceConversation row = new AnnounceConversation();
            row.setId(idOf(envelope));
            row.setStatus(ConversationStatus.CLOSED.name());
            row.setClosedTime(event.at());
            row.setLastEventId(envelope.eventId());
            row.setUpdateTime(event.at());
            return conversations.updateByPrimaryKeySelective(row).then();
        });
        handlers.on(ConversationBlocked.class, (event, envelope) -> {
            AnnounceConversation row = new AnnounceConversation();
            row.setId(idOf(envelope));
            row.setStatus(ConversationStatus.BLOCKED.name());
            row.setBlockedReason(event.reason());
            row.setLastEventId(envelope.eventId());
            row.setUpdateTime(event.at());
            return conversations.updateByPrimaryKeySelective(row).then();
        });
    }

    @Override
    public Mono<Void> reset() {
        return Flux.just("announce_chat_message", "announce_conversation_participant", "announce_conversation")
                .concatMap(table -> db.sql("delete from " + table).then())
                .then();
    }

    private static AnnounceConversationParticipant participantRow(String conversationId, Participant p, LocalDateTime at) {
        AnnounceConversationParticipant row = new AnnounceConversationParticipant();
        row.setConversationId(conversationId);
        row.setParticipantType(p.type().name());
        row.setParticipantId(p.id());
        row.setRole(p.role().name());
        row.setJoinedTime(at);
        row.setLastReadSeq(0);
        row.setMuted(false);
        row.setAddTime(at);
        row.setUpdateTime(at);
        row.setDeleted(false);
        return row;
    }

    static BigDecimal cents(Long cents) {
        return cents == null ? null : BigDecimal.valueOf(cents, 2);
    }

    private static String idOf(EventEnvelope envelope) {
        return Subjects.idOf(envelope.subject());
    }
}
