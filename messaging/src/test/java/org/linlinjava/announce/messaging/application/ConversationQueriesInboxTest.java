package org.linlinjava.announce.messaging.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.AnnounceChatMessageMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceConversationParticipantMapper;
import org.linlinjava.announce.db.domain.AnnounceConversation;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipant;
import org.linlinjava.announce.db.domain.AnnounceConversationParticipantExample;
import org.linlinjava.announce.messaging.application.ConversationQueries.ConversationView;
import org.linlinjava.announce.messaging.application.ConversationQueries.Viewer;
import org.linlinjava.announce.messaging.domain.conversation.ConversationStatus;
import org.linlinjava.announce.messaging.domain.conversation.ParticipantType;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A page of conversations costs two statements whatever its size: the page, then the participants of every
 * conversation on it in one {@code IN} query (tuning step 7). The views keep the page order and compute
 * {@code unread} for the viewer alone.
 */
class ConversationQueriesInboxTest {

    static final int MEMBER = 7;
    static final int STAFF = 9_950;
    static final Viewer MEMBER_VIEWER = new Viewer(ParticipantType.USER, MEMBER);
    static final Viewer STAFF_VIEWER = new Viewer(ParticipantType.ADMIN, STAFF);

    final AnnounceConversationMapper conversations = mock(AnnounceConversationMapper.class);
    final AnnounceConversationParticipantMapper participants = mock(AnnounceConversationParticipantMapper.class);
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final ConversationQueries queries = new ConversationQueries(conversations, participants, mock(AnnounceChatMessageMapper.class),
            meters, new MessagingProperties());

    @Test
    void anInboxPageReadsTheParticipantsOfEveryConversationInOneQueryAndKeepsThePageOrder() {
        List<AnnounceConversation> page = List.of(conversation("c3", 30), conversation("c1", 10), conversation("c2", 20));
        when(conversations.selectByParticipant(eq("USER"), eq(MEMBER), anyLong(), eq(20))).thenReturn(Flux.fromIterable(page));
        // the IN query answers in conversation_id order, not in page order
        when(participants.selectByExample(any())).thenReturn(Flux.just(
                participant("c1", MEMBER, "MEMBER", 10), participant("c1", STAFF, "CSR", 0),
                participant("c2", MEMBER, "MEMBER", 5), participant("c2", STAFF, "CSR", 0),
                participant("c3", MEMBER, "MEMBER", 0), participant("c3", STAFF, "CSR", 30)));

        List<ConversationView> views = queries.inbox(MEMBER_VIEWER, 1, 20).collectList().block();

        assertThat(views).extracting(ConversationView::id).containsExactly("c3", "c1", "c2");
        assertThat(views).extracting(ConversationView::unread).containsExactly(30, 0, 15);
        assertThat(views.get(0).participants()).hasSize(2);
        ArgumentCaptor<AnnounceConversationParticipantExample> example = ArgumentCaptor.forClass(AnnounceConversationParticipantExample.class);
        verify(participants, times(1)).selectByExample(example.capture());
        assertThat(example.getValue().getOredCriteria().get(0).getAllCriteria())
                .anySatisfy(criterion -> {
                    assertThat(criterion.getCondition()).isEqualTo("conversation_id in");
                    assertThat((List<?>) criterion.getValue()).isEqualTo(List.of("c3", "c1", "c2"));
                });
        assertThat(meters.get("messaging.query.latency").tag("query", "inbox").tag("outcome", "ok").timer().count()).isEqualTo(1);
    }

    @Test
    void anEmptyPageAsksForNoParticipants() {
        when(conversations.selectByParticipant(anyString(), any(), anyLong(), eq(20))).thenReturn(Flux.empty());

        assertThat(queries.inbox(MEMBER_VIEWER, 3, 20).collectList().block()).isEmpty();

        verify(participants, never()).selectByExample(any());
    }

    @Test
    void theStaffQueueIsBatchedTheSameWayAndShowsNoUnreadForAnOutsider() {
        when(conversations.selectPageByExample(any(), anyLong(), eq(50))).thenReturn(Flux.just(conversation("c1", 3), conversation("c2", 4)));
        when(participants.selectByExample(any())).thenReturn(Flux.just(
                participant("c1", MEMBER, "MEMBER", 0), participant("c2", MEMBER + 1, "MEMBER", 0)));

        List<ConversationView> views = queries.byStatus(ConversationStatus.WAITING, 1, 50).collectList().block();

        assertThat(views).extracting(ConversationView::id).containsExactly("c1", "c2");
        assertThat(views).extracting(ConversationView::unread).containsExactly(null, null);
        verify(participants, times(1)).selectByExample(any());
        assertThat(meters.get("messaging.query.latency").tag("query", "by_status").timer().count()).isEqualTo(1);
    }

    @Test
    void aConversationWithoutParticipantRowsStillRendersWithAnEmptyList() {
        when(conversations.selectByParticipant(eq("ADMIN"), eq(STAFF), anyLong(), eq(20))).thenReturn(Flux.just(conversation("c1", 1)));
        when(participants.selectByExample(any())).thenReturn(Flux.empty());

        List<ConversationView> views = queries.inbox(STAFF_VIEWER, 1, 20).collectList().block();

        assertThat(views).hasSize(1);
        assertThat(views.get(0).participants()).isEmpty();
        assertThat(views.get(0).unread()).isNull();
    }

    static AnnounceConversation conversation(String id, int lastSeq) {
        AnnounceConversation row = new AnnounceConversation();
        row.setId(id);
        row.setType("SUPPORT");
        row.setStatus("OPEN");
        row.setLastMessageSeq(lastSeq);
        row.setMessageCount(lastSeq);
        return row;
    }

    static AnnounceConversationParticipant participant(String conversationId, int id, String role, int lastReadSeq) {
        AnnounceConversationParticipant p = new AnnounceConversationParticipant();
        p.setConversationId(conversationId);
        p.setParticipantType(role.equals("CSR") ? "ADMIN" : "USER");
        p.setParticipantId(id);
        p.setRole(role);
        p.setLastReadSeq(lastReadSeq);
        return p;
    }
}
