package org.linlinjava.announce.messaging.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.cqrs.command.CommandRejectedException;
import org.linlinjava.announce.cqrs.command.CqrsProblemDetailsHandler;
import org.linlinjava.announce.messaging.application.ConversationQueries;
import org.linlinjava.announce.messaging.application.Conversations;
import org.linlinjava.announce.messaging.domain.conversation.ContentType;
import org.linlinjava.announce.messaging.domain.conversation.Conversation;
import org.linlinjava.announce.messaging.domain.conversation.ConversationStatus;
import org.linlinjava.announce.messaging.domain.conversation.ConversationType;
import org.linlinjava.announce.messaging.domain.conversation.Participant;
import org.linlinjava.announce.messaging.domain.conversation.PostResult;
import org.linlinjava.announce.messaging.domain.conversation.Sender;
import org.linlinjava.announce.messaging.interfaces.Principals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The REST surface without containers: principal headers, body mapping, problem details. */
@WebFluxTest(controllers = ConversationController.class)
@Import(CqrsProblemDetailsHandler.class)
class ConversationControllerTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 12, 0);

    @Autowired WebTestClient client;
    @MockBean Conversations conversations;
    @MockBean ConversationQueries queries;

    static Conversation listing() {
        return new Conversation("c1", ConversationType.LISTING, "a1", 7, 8, null, ConversationStatus.OPEN,
                List.of(Participant.buyer(7), Participant.seller(8)), 0, Map.of(), null, null, NOW, NOW);
    }

    @Test
    void openingNeedsTheUserHeader() {
        client.post().uri("/api/conversations").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"type\":\"LISTING\",\"announceId\":\"a1\"}")
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void openingAListingConversationAnswersWithTheState() {
        when(conversations.openListing(7, "a1", "cmd-1")).thenReturn(Mono.just(listing()));
        client.post().uri("/api/conversations").header(Principals.USER_HEADER, "7").header(Principals.COMMAND_ID_HEADER, "cmd-1")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"type\":\"LISTING\",\"announceId\":\"a1\"}")
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.id").isEqualTo("c1").jsonPath("$.status").isEqualTo("OPEN").jsonPath("$.participants[1].role").isEqualTo("SELLER")
                .jsonPath("$.messagesByClientId").doesNotExist();
    }

    @Test
    void postingMapsTheBodyToACommandAndRejectionsToProblemDetails() {
        when(conversations.post(eq("c1"), eq(Sender.user(7)), eq(ContentType.OFFER), isNull(), isNull(), eq(90000L), eq("SEK"), eq("m1"), isNull()))
                .thenReturn(Mono.just(new PostResult("c1", "msg-1", 3, "m1", false, NOW)));
        client.post().uri("/api/conversations/c1/messages").header(Principals.USER_HEADER, "7").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"clientMsgId\":\"m1\",\"contentType\":\"offer\",\"amountCents\":90000,\"currency\":\"SEK\"}")
                .exchange().expectStatus().isCreated().expectBody().jsonPath("$.seq").isEqualTo(3).jsonPath("$.messageId").isEqualTo("msg-1");

        when(conversations.post(eq("c1"), eq(Sender.user(9)), eq(ContentType.TEXT), eq("hi"), isNull(), isNull(), isNull(), eq("m2"), isNull()))
                .thenReturn(Mono.error(new CommandRejectedException(HttpStatus.FORBIDDEN, "not-participant", "Only participants may act on this conversation")));
        client.post().uri("/api/conversations/c1/messages").header(Principals.USER_HEADER, "9").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"clientMsgId\":\"m2\",\"content\":\"hi\"}")
                .exchange().expectStatus().isForbidden().expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("not-participant");
    }

    @Test
    void readsNeedAPrincipalAndStaffCountsAsOne() {
        client.get().uri("/api/conversations/c1").exchange().expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("no-principal");
        client.get().uri("/api/conversations/c1").header(Principals.USER_HEADER, "abc").exchange().expectStatus().isBadRequest();

        when(queries.messages(eq("c1"), any(), eq(5), eq(10))).thenReturn(reactor.core.publisher.Flux.empty());
        client.get().uri("/api/conversations/c1/messages?after=5&limit=10").header(Principals.STAFF_HEADER, "3").exchange().expectStatus().isOk()
                .expectBody().json("[]");
        verify(queries).messages(eq("c1"), eq(new ConversationQueries.Viewer(org.linlinjava.announce.messaging.domain.conversation.ParticipantType.ADMIN, 3)), eq(5), eq(10));
    }

    @Test
    void readMarkerIsANoContentWrite() {
        when(conversations.markRead("c1", org.linlinjava.announce.messaging.domain.conversation.ParticipantType.USER, 7, 4)).thenReturn(Mono.empty());
        client.post().uri("/api/conversations/c1/read").header(Principals.USER_HEADER, "7").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"seq\":4}").exchange().expectStatus().isNoContent();
    }
}
