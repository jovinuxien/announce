package org.linlinjava.announce.messaging.application;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.AnnounceChatMessageMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationParticipantMapper;
import org.linlinjava.announce.db.domain.AnnounceConversation;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipant;
import org.linlinjava.announce.messaging.application.ConversationQueries.Viewer;
import org.linlinjava.announce.messaging.domain.conversation.ParticipantType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The socket handshake asks the read model, which trails the command side: a conversation that is not there yet
 * is looked up again a bounded number of times; one that is there answers at once.
 */
class ConversationQueriesAttachTest {

    static final int BUYER = 7;
    static final int SELLER = 8;
    static final int STRANGER = 9;
    static final int RECHECKS = 3;

    final AnnounceConversationMapper conversations = mock(AnnounceConversationMapper.class);
    final AnnounceConversationParticipantMapper participants = mock(AnnounceConversationParticipantMapper.class);
    final AtomicInteger lookups = new AtomicInteger();

    ConversationQueries queries() {
        MessagingProperties properties = new MessagingProperties();
        properties.getAttach().setRechecks(RECHECKS);
        properties.getAttach().setRecheckInterval(Duration.ofMillis(5));
        when(participants.selectByExample(any())).thenReturn(Flux.just(participant(BUYER, "BUYER"), participant(SELLER, "SELLER")));
        return new ConversationQueries(conversations, participants, mock(AnnounceChatMessageMapper.class), properties);
    }

    /** The projection writes the row after {@code missingLookups} look-ups found nothing. */
    void rowAppearsAfter(int missingLookups) {
        when(conversations.selectByPrimaryKey("c1")).thenReturn(Mono.defer(() ->
                lookups.incrementAndGet() <= missingLookups ? Mono.empty() : Mono.just(row())));
    }

    static AnnounceConversation row() {
        AnnounceConversation row = new AnnounceConversation();
        row.setId("c1");
        row.setType("LISTING");
        row.setStatus("OPEN");
        row.setBuyerId(BUYER);
        row.setSellerId(SELLER);
        return row;
    }

    static AnnounceConversationParticipant participant(int id, String role) {
        AnnounceConversationParticipant p = new AnnounceConversationParticipant();
        p.setConversationId("c1");
        p.setParticipantType(ParticipantType.USER.name());
        p.setParticipantId(id);
        p.setRole(role);
        p.setLastReadSeq(0);
        return p;
    }

    static Viewer member(int id) {
        return new Viewer(ParticipantType.USER, id);
    }

    @Test
    void aParticipantAttachesAtOnceWhenTheReadModelHasTheConversation() {
        rowAppearsAfter(0);
        StepVerifier.create(queries().mayAttach("c1", member(BUYER))).expectNext(true).verifyComplete();
        assertThat(lookups).hasValue(1);
    }

    @Test
    void aConversationTheReadModelDoesNotHaveYetIsLookedUpAgain() {
        rowAppearsAfter(2);
        StepVerifier.create(queries().mayAttach("c1", member(BUYER))).expectNext(true).verifyComplete();
        assertThat(lookups).hasValue(3);
    }

    @Test
    void theLookUpsAreBoundedAndThenTheSocketIsRefused() {
        rowAppearsAfter(Integer.MAX_VALUE);
        StepVerifier.create(queries().mayAttach("c1", member(BUYER))).expectNext(false).verifyComplete();
        assertThat(lookups).as("the first look-up and the re-checks").hasValue(1 + RECHECKS);
    }

    @Test
    void aStrangerIsRefusedAtOnceWithoutWaiting() {
        rowAppearsAfter(0);
        StepVerifier.create(queries().mayAttach("c1", member(STRANGER))).expectNext(false).verifyComplete();
        assertThat(lookups).hasValue(1);
    }

    @Test
    void staffAttachToAnyConversationOnceItIsThere() {
        rowAppearsAfter(1);
        StepVerifier.create(queries().mayAttach("c1", new Viewer(ParticipantType.ADMIN, 3))).expectNext(true).verifyComplete();
        assertThat(lookups).hasValue(2);
    }
}
