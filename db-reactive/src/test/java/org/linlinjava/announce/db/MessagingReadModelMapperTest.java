package org.linlinjava.announce.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.AnnounceChatMessageMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationParticipantMapper;
import org.linlinjava.announce.db.domain.AnnounceChatMessage;
import org.linlinjava.announce.db.domain.AnnounceChatMessageExample;
import org.linlinjava.announce.db.domain.AnnounceConversation;
import org.linlinjava.announce.db.domain.AnnounceConversationExample;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipant;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipantExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The messaging read model (sql/announce_messaging.sql): conversation and message rows keyed by the ids the
 * Conversation aggregate assigns, written with the idempotent hand-written statements the projection uses.
 */
@SpringBootTest
class MessagingReadModelMapperTest {

    private static final String PREFIX = "it-conv-";
    private static final String CONVERSATION = PREFIX + "1";
    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired private AnnounceConversationMapper conversations;
    @Autowired private AnnounceConversationParticipantMapper participants;
    @Autowired private AnnounceChatMessageMapper messages;

    @AfterEach
    void cleanUp() {
        AnnounceChatMessageExample m = new AnnounceChatMessageExample();
        m.or().andConversationIdLike(PREFIX + "%");
        messages.deleteByExample(m).block();
        AnnounceConversationParticipantExample p = new AnnounceConversationParticipantExample();
        p.or().andConversationIdLike(PREFIX + "%");
        participants.deleteByExample(p).block();
        AnnounceConversationExample c = new AnnounceConversationExample();
        c.or().andIdLike(PREFIX + "%");
        conversations.deleteByExample(c).block();
    }

    @Test
    void upsertsReplayWithoutDuplicatesAndOffersAreTracked() {
        AnnounceConversation conversation = conversation();
        StepVerifier.create(conversations.upsert(conversation)).expectNext(1).verifyComplete();
        conversation.setLastMessageSeq(1);
        conversation.setMessageCount(1);
        conversation.setLastEventId("e2");
        // second write of the same id updates in place (MySQL reports 2 affected rows for an update on duplicate key)
        StepVerifier.create(conversations.upsert(conversation)).expectNextMatches(n -> n == 1 || n == 2).verifyComplete();
        StepVerifier.create(conversations.selectByPrimaryKey(CONVERSATION))
                .assertNext(row -> {
                    assertEquals("LISTING", row.getType());
                    assertEquals(1, row.getMessageCount());
                    assertEquals("e2", row.getLastEventId());
                    assertEquals(7, row.getBuyerId());
                })
                .verifyComplete();

        AnnounceConversationParticipant buyer = participant("USER", 7, "BUYER");
        StepVerifier.create(participants.upsert(buyer)).expectNext(1).verifyComplete();
        StepVerifier.create(participants.upsert(participant("USER", 7, "BUYER"))).expectNextMatches(n -> n >= 0).verifyComplete();
        StepVerifier.create(participants.upsert(participant("USER", 8, "SELLER"))).expectNext(1).verifyComplete();
        AnnounceConversationParticipantExample all = new AnnounceConversationParticipantExample();
        all.or().andConversationIdEqualTo(CONVERSATION);
        StepVerifier.create(participants.countByExample(all)).expectNext(2L).verifyComplete();

        AnnounceChatMessage offer = message(PREFIX + "m1", 1, "OFFER", "I offer 900");
        offer.setOfferAmount(new BigDecimal("900.00"));
        offer.setOfferCurrency("SEK");
        offer.setOfferStatus("OPEN");
        StepVerifier.create(messages.upsert(offer)).expectNext(1).verifyComplete();
        StepVerifier.create(messages.updateOfferStatus(PREFIX + "m1", "ACCEPTED", NOW)).expectNext(1).verifyComplete();
        // a replayed MessagePosted must not reset the outcome
        StepVerifier.create(messages.upsert(offer)).expectNextMatches(n -> n >= 0).verifyComplete();
        StepVerifier.create(messages.upsert(message(PREFIX + "m2", 2, "TEXT", "hello"))).expectNext(1).verifyComplete();

        StepVerifier.create(messages.selectAfter(CONVERSATION, 0, 10))
                .assertNext(m -> {
                    assertEquals(1, m.getSeq());
                    assertEquals("ACCEPTED", m.getOfferStatus());
                    assertEquals("I offer 900", m.getContent(), "selectAfter includes the BLOB content column");
                    assertEquals(new BigDecimal("900.00"), m.getOfferAmount());
                })
                .assertNext(m -> assertEquals("hello", m.getContent()))
                .verifyComplete();
        StepVerifier.create(messages.selectAfter(CONVERSATION, 1, 10)).expectNextCount(1).verifyComplete();

        conversation.setOpenOfferMessageId(null);
        conversation.setOpenOfferAmount(null);
        conversation.setOpenOfferCurrency(null);
        conversation.setAcceptedOfferAmount(new BigDecimal("900.00"));
        conversation.setAcceptedOfferCurrency("SEK");
        conversation.setAcceptedOfferTime(NOW);
        StepVerifier.create(conversations.updateOffers(conversation)).expectNext(1).verifyComplete();
        StepVerifier.create(conversations.selectByPrimaryKey(CONVERSATION))
                .assertNext(row -> {
                    assertNull(row.getOpenOfferMessageId());
                    assertEquals(new BigDecimal("900.00"), row.getAcceptedOfferAmount());
                })
                .verifyComplete();

        StepVerifier.create(participants.raiseLastReadSeq(CONVERSATION, "USER", 7, 2, NOW)).expectNext(1).verifyComplete();
        StepVerifier.create(participants.raiseLastReadSeq(CONVERSATION, "USER", 7, 1, NOW)).expectNextMatches(n -> n >= 0).verifyComplete();
        AnnounceConversationParticipantExample mine = new AnnounceConversationParticipantExample();
        mine.or().andConversationIdEqualTo(CONVERSATION).andParticipantIdEqualTo(7);
        StepVerifier.create(participants.selectOneByExample(mine).map(AnnounceConversationParticipant::getLastReadSeq))
                .expectNext(2).verifyComplete();

        StepVerifier.create(conversations.selectByParticipant("USER", 8, 0, 10).map(AnnounceConversation::getId))
                .expectNext(CONVERSATION).verifyComplete();
        StepVerifier.create(conversations.selectByParticipant("USER", 9, 0, 10)).verifyComplete();
    }

    private static AnnounceConversation conversation() {
        AnnounceConversation c = new AnnounceConversation();
        c.setId(CONVERSATION);
        c.setType("LISTING");
        c.setAnnounceId(PREFIX + "announce");
        c.setBuyerId(7);
        c.setSellerId(8);
        c.setStatus("OPEN");
        c.setCreatedByType("USER");
        c.setCreatedBy(7);
        c.setLastMessageSeq(0);
        c.setMessageCount(0);
        c.setLastEventId("e1");
        c.setAddTime(NOW);
        c.setUpdateTime(NOW);
        c.setDeleted(false);
        return c;
    }

    private static AnnounceConversationParticipant participant(String type, int id, String role) {
        AnnounceConversationParticipant p = new AnnounceConversationParticipant();
        p.setConversationId(CONVERSATION);
        p.setParticipantType(type);
        p.setParticipantId(id);
        p.setRole(role);
        p.setJoinedTime(NOW);
        p.setLastReadSeq(0);
        p.setMuted(false);
        p.setAddTime(NOW);
        p.setUpdateTime(NOW);
        p.setDeleted(false);
        return p;
    }

    private static AnnounceChatMessage message(String id, int seq, String type, String content) {
        AnnounceChatMessage m = new AnnounceChatMessage();
        m.setId(id);
        m.setConversationId(CONVERSATION);
        m.setSeq(seq);
        m.setSenderType("USER");
        m.setSenderId(7);
        m.setContentType(type);
        m.setContent(content);
        m.setClientMsgId("c-" + seq);
        m.setSentTime(NOW);
        m.setAddTime(NOW);
        m.setUpdateTime(NOW);
        m.setDeleted(false);
        return m;
    }
}
