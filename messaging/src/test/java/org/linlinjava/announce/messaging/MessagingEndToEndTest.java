package org.linlinjava.announce.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.linlinjava.announce.cqrs.messaging.PublicEventHeaders;
import org.linlinjava.announce.cqrs.messaging.PublicEventTypeRegistry;
import org.linlinjava.announce.cqrs.security.ServiceTokenWebFilter;
import org.linlinjava.announce.cqrs.security.ServiceTokens;
import org.linlinjava.announce.cqrs.test.EdgeRequests;
import org.linlinjava.announce.cqrs.test.containers.CqrsContainers;
import org.linlinjava.announce.cqrs.test.containers.EventSourcingDbContainer;
import org.linlinjava.announce.messaging.application.AnnounceLookup;
import org.linlinjava.announce.messaging.application.AnnounceRef;
import org.linlinjava.announce.messaging.domain.conversation.ConversationIds;
import org.linlinjava.announce.messaging.infrastructure.live.LiveSessions;
import org.linlinjava.announce.order.api.OrderAgreedV1;
import org.linlinjava.announce.order.api.OrderPaidV1;
import org.linlinjava.announce.messaging.interfaces.Principals;
import org.linlinjava.announce.messaging.interfaces.ws.ConversationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.linlinjava.announce.messaging.infrastructure.metrics.MessagingMeters;
import org.linlinjava.announce.messaging.infrastructure.projection.ConversationReadModelProjection;
import org.linlinjava.announce.messaging.infrastructure.messaging.MessagingRelays;
import static org.awaitility.Awaitility.await;

/**
 * The whole path: REST and WebSocket commands -> EventSourcingDB (container) -> read model in the live
 * {@code announce_reactive_db} -> live fan-out over Kafka (container) back to the sockets, plus the public topic
 * {@code messaging.v1} and the {@code order.v1} subscriber. One node, two WebSocket clients.
 *
 * <p>The read model is the live database, so the class removes what it wrote, and before the context starts it
 * drops the checkpoints of the three projection groups and any leftover rows of earlier runs: the checkpoints
 * refer to event ids of an EventSourcingDB container that may no longer exist.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // as in production: only requests and handshakes signed by an edge are served (the test profile alone says off)
        properties = {EdgeRequests.ENFORCE, EdgeRequests.SECRET_PROPERTY})
@ActiveProfiles("test")
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MessagingEndToEndTest {

    static final Duration WAIT = Duration.ofSeconds(60);
    static final String ANNOUNCE = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
    static final String OTHER_ANNOUNCE = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
    static final int BUYER = 7;
    static final int SELLER = 8;
    static final int STRANGER = 9;
    static final int OTHER_BUYER = 10;
    static final int STAFF = 3;
    static final int OTHER_STAFF = 4;
    static final String CONVERSATION = ConversationIds.listing(ANNOUNCE, BUYER);
    static final String RACE_ANNOUNCE = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
    /** A second listing conversation, for the tests that race the read model and each other. */
    static final String RACE = ConversationIds.listing(RACE_ANNOUNCE, BUYER);
    static final List<String> conversations = new CopyOnWriteArrayList<>();
    static final List<Integer> bulletins = new CopyOnWriteArrayList<>();
    static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    static {
        cleanLiveDatabase();
    }

    @Container
    static final EventSourcingDbContainer esdb = CqrsContainers.eventSourcingDb();
    @Container
    static final KafkaContainer kafka = CqrsContainers.kafka();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        CqrsContainers.register(registry, esdb, kafka, null);
        registry.add("messaging.announce-lookup", () -> "stub");
    }

    /** Stands in for the announce-management API and records what reaches the public topic. */
    @TestConfiguration
    static class Stubs {
        static final List<Map<String, Object>> published = new CopyOnWriteArrayList<>();
        static final List<Map<String, String>> publishedHeaders = new CopyOnWriteArrayList<>();

        /** The autowired client stands for an edge: each of its requests carries a fresh X-Service-Token. */
        @Bean
        org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer edgeSignedClient() {
            return EdgeRequests.signedWebTestClient();
        }

        @Bean
        AnnounceLookup stubAnnounceLookup() {
            return id -> id.startsWith("e2e-") ? Mono.just(new AnnounceRef(id, SELLER, "E2E bike", "PUBLISHED")) : Mono.empty();
        }

        @Bean
        Consumer<Message<byte[]>> publicEventsProbe(ObjectMapper objectMapper) {
            return message -> {
                try {
                    published.add(objectMapper.readValue(message.getPayload(), MAP));
                    Map<String, String> headers = new LinkedHashMap<>();
                    message.getHeaders().forEach((k, v) -> headers.put(k, v instanceof byte[] b ? new String(b, StandardCharsets.UTF_8) : String.valueOf(v)));
                    publishedHeaders.add(headers);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
        }
    }

    @Autowired WebTestClient client;
    @Autowired LiveSessions sessions;
    @Autowired StreamBridge streamBridge;
    @Autowired ObjectMapper objectMapper;
    @Autowired MeterRegistry meters;
    @Autowired io.r2dbc.spi.ConnectionFactory connectionFactory;
    @LocalServerPort int port;

    static DatabaseClient db;

    @BeforeEach
    void keepDatabaseClient() {
        db = DatabaseClient.create(connectionFactory);
    }

    @AfterAll
    static void removeWhatWeWrote() {
        if (db == null) {
            return;
        }
        for (String id : conversations) {
            db.sql("delete from announce_chat_message where conversation_id = :id").bind("id", id).then().block(WAIT);
            db.sql("delete from announce_conversation_participant where conversation_id = :id").bind("id", id).then().block(WAIT);
            db.sql("delete from announce_conversation where id = :id").bind("id", id).then().block(WAIT);
        }
        for (Integer id : bulletins) {
            db.sql("delete from announce_bulletin where id = :id").bind("id", id).then().block(WAIT);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** One WebSocket client: sends JSON frames and keeps every frame it received. */
    class WsClient {
        final List<Map<String, Object>> received = new CopyOnWriteArrayList<>();
        final Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        final Sinks.One<CloseStatus> closed = Sinks.one();
        Disposable connection;

        WsClient connect(String path, String type, int id) {
            HttpHeaders headers = new HttpHeaders();
            headers.add(ServiceTokens.HEADER, EdgeRequests.token());   // the handshake comes through an edge
            if (type != null) {
                identity(headers, type, id);
            }
            connection = new ReactorNettyWebSocketClient().execute(URI.create("ws://localhost:" + port + path), headers, session -> {
                session.closeStatus().subscribe(closed::tryEmitValue);
                Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                Mono<Void> receive = session.receive().map(WebSocketMessage::getPayloadAsText).doOnNext(text -> received.add(parse(text))).then();
                return Mono.when(send, receive);
            }).subscribe();
            return this;
        }

        void send(Map<String, Object> frame) {
            try {
                outbound.tryEmitNext(objectMapper.writeValueAsString(frame));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        Map<String, Object> awaitFrame(String type, java.util.function.Predicate<Map<String, Object>> matching) {
            await().atMost(WAIT).until(() -> received.stream().anyMatch(f -> type.equals(f.get("type")) && matching.test(f)));
            return received.stream().filter(f -> type.equals(f.get("type")) && matching.test(f)).findFirst().orElseThrow();
        }

        /** The {@code ack} or {@code error} that answers the frame sent with this client message id. */
        Map<String, Object> awaitAnswer(String clientMsgId) {
            java.util.function.Predicate<Map<String, Object>> answer = f -> clientMsgId.equals(f.get("clientMsgId"))
                    && ("ack".equals(f.get("type")) || "error".equals(f.get("type")));
            await().atMost(WAIT).until(() -> received.stream().anyMatch(answer));
            return received.stream().filter(answer).findFirst().orElseThrow();
        }

        CloseStatus awaitClose() {
            return closed.asMono().block(WAIT);
        }

        void close() {
            outbound.tryEmitComplete();
            if (connection != null) {
                connection.dispose();
            }
        }
    }

    Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, MAP);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    WebTestClient.ResponseSpec open(int user, String body) {
        return client.post().uri("/api/conversations").headers(h -> { h.set(Principals.USER_HEADER, String.valueOf(String.valueOf(user))); h.set(Principals.TYPE_HEADER, "MEMBER"); })
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    /** {@code type} is {@link #AS_MEMBER} or {@link #AS_STAFF}: what the customer or the admin edge forwards. */
    WebTestClient.ResponseSpec get(String uri, String type, int id) {
        return client.get().uri(uri).headers(h -> identity(h, type, id)).exchange();
    }

    static final String AS_MEMBER = "MEMBER";
    static final String AS_STAFF = "ADMIN";

    static void identity(HttpHeaders headers, String type, int id) {
        headers.set(Principals.USER_HEADER, String.valueOf(id));
        headers.set(Principals.TYPE_HEADER, type);
    }

    void publishOrderEvent(Object event, String eventId) {
        PublicEventTypeRegistry.Key key = PublicEventTypeRegistry.keyOf(event.getClass());
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(PublicEventHeaders.EVENT_TYPE, key.type());
        headers.put(PublicEventHeaders.EVENT_VERSION, String.valueOf(key.version()));
        headers.put(PublicEventHeaders.EVENT_ID, eventId);
        headers.put(PublicEventHeaders.SUBJECT, "/order/o-1");
        headers.put(KafkaHeaders.KEY, "/order/o-1".getBytes(StandardCharsets.UTF_8));
        headers.put("contentType", MimeTypeUtils.APPLICATION_JSON_VALUE);
        try {
            assertThat(streamBridge.send("order.v1", MessageBuilder.withPayload(objectMapper.writeValueAsBytes(event)).copyHeaders(headers).build())).isTrue();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void cleanLiveDatabase() {
        String url = System.getenv().getOrDefault("ANNOUNCE_JDBC_URL", "jdbc:mysql://localhost:3306/announce_reactive_db");
        String user = System.getenv().getOrDefault("ANNOUNCE_DB_USER", "announce");
        String password = System.getenv().getOrDefault("ANNOUNCE_DB_PASSWORD", "Calliste_1006");
        List<String> statements = List.of(
                "delete from cqrs_readside_progress where group_id in ('conversation-read-model', 'messaging-public', 'messaging-live')",
                "delete from announce_chat_message where conversation_id in (select id from announce_conversation where announce_id like 'e2e-%')",
                "delete from announce_conversation_participant where conversation_id in (select id from announce_conversation where announce_id like 'e2e-%')",
                "delete from announce_conversation where announce_id like 'e2e-%' or subject like 'e2e %'",
                "delete from announce_bulletin where content like 'e2e %'");
        try (Connection c = DriverManager.getConnection(url, user, password); Statement s = c.createStatement()) {
            for (String statement : statements) {
                try {
                    s.executeUpdate(statement);
                } catch (java.sql.SQLSyntaxErrorException missingTable) {
                    // the framework creates cqrs_readside_progress on the first start-up: nothing to clean yet
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("The live database is needed for the read model: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    @Order(0)
    void onlyRequestsAndHandshakesThatPassedAnEdgeAreServed() {
        WebTestClient direct = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        // identity headers alone are worth nothing: without the edge's token a member, and "staff", are refused
        direct.get().uri("/api/conversations").headers(h -> { h.set(Principals.USER_HEADER, String.valueOf(String.valueOf(BUYER))); h.set(Principals.TYPE_HEADER, "MEMBER"); }).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.type").isEqualTo(ServiceTokenWebFilter.PROBLEM_TYPE_MISSING);
        direct.get().uri("/api/admin/conversations?status=WAITING").headers(h -> { h.set(Principals.USER_HEADER, String.valueOf(String.valueOf(STAFF))); h.set(Principals.TYPE_HEADER, "ADMIN"); }).exchange()
                .expectStatus().isUnauthorized();
        direct.get().uri("/api/conversations").headers(h -> { h.set(Principals.USER_HEADER, String.valueOf(String.valueOf(STAFF))); h.set(Principals.TYPE_HEADER, "ADMIN"); })
                .header(ServiceTokens.HEADER, ServiceTokens.mint("gateway-api", "guessed", java.time.Instant.now().getEpochSecond())).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.type").isEqualTo(ServiceTokenWebFilter.PROBLEM_TYPE_INVALID);
        // the WebSocket handshake is an HTTP request like any other
        HttpHeaders unsigned = new HttpHeaders();
        identity(unsigned, AS_STAFF, STAFF);
        assertThatThrownBy(() -> new ReactorNettyWebSocketClient()
                .execute(URI.create("ws://localhost:" + port + "/ws/staff?clientType=desktop"), unsigned, session -> Mono.empty())
                .block(WAIT)).hasStackTraceContaining("401");
        // health checks reach the service directly
        direct.get().uri("/actuator/health").exchange().expectStatus().value(status -> assertThat(status).isNotEqualTo(401));

        get("/api/conversations", AS_MEMBER, BUYER).expectStatus().isOk();
    }

    @Test
    @Order(1)
    void aBuyerOpensTheListingConversationOnceAndReadsItBack() {
        conversations.add(CONVERSATION);
        open(BUYER, "{\"type\":\"LISTING\",\"announceId\":\"" + ANNOUNCE + "\"}").expectStatus().isCreated()
                .expectBody().jsonPath("$.id").isEqualTo(CONVERSATION).jsonPath("$.status").isEqualTo("OPEN")
                .jsonPath("$.sellerId").isEqualTo(SELLER).jsonPath("$.participants.length()").isEqualTo(2);
        // the same pair again finds the same conversation
        open(BUYER, "{\"type\":\"LISTING\",\"announceId\":\"" + ANNOUNCE + "\"}").expectStatus().isCreated()
                .expectBody().jsonPath("$.id").isEqualTo(CONVERSATION);
        open(SELLER, "{\"type\":\"LISTING\",\"announceId\":\"" + ANNOUNCE + "\"}").expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("buyer-is-seller");
        open(BUYER, "{\"type\":\"LISTING\",\"announceId\":\"missing\"}").expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("announce-not-found");

        await().atMost(WAIT).untilAsserted(() -> get("/api/conversations/" + CONVERSATION, AS_MEMBER, BUYER).expectStatus().isOk()
                .expectBody().jsonPath("$.type").isEqualTo("LISTING").jsonPath("$.announceId").isEqualTo(ANNOUNCE)
                .jsonPath("$.participants.length()").isEqualTo(2).jsonPath("$.unread").isEqualTo(0));
        get("/api/conversations/" + CONVERSATION, AS_MEMBER, STRANGER).expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("not-participant");
        get("/api/conversations/" + CONVERSATION, AS_STAFF, STAFF).expectStatus().isOk();
        client.get().uri("/api/conversations/" + CONVERSATION).exchange().expectStatus().isUnauthorized();
        get("/api/conversations", AS_MEMBER, SELLER).expectStatus().isOk()
                .expectBody().jsonPath("$.items[?(@.id == '" + CONVERSATION + "')]").exists();
    }

    @Test
    @Order(2)
    void twoWebSocketClientsExchangeMessagesAndAnOfferIsAcceptedOntoThePublicTopic() {
        WsClient buyer = new WsClient().connect("/ws/conversations/" + CONVERSATION, AS_MEMBER, BUYER);
        WsClient seller = new WsClient().connect("/ws/conversations/" + CONVERSATION, AS_MEMBER, SELLER);
        await().atMost(WAIT).until(() -> sessions.sessions(CONVERSATION) == 2);
        try {
            buyer.send(Map.of("type", "post", "clientMsgId", "c1", "content", "Is it still available?"));
            Map<String, Object> ack = buyer.awaitFrame("ack", f -> "c1".equals(f.get("clientMsgId")));
            assertThat(ack).containsEntry("seq", 1).containsEntry("duplicate", false);
            Map<String, Object> seen = seller.awaitFrame("message", f -> Integer.valueOf(1).equals(f.get("seq")));
            assertThat(seen).containsEntry("content", "Is it still available?").containsEntry("senderId", BUYER).containsEntry("contentType", "TEXT");

            seller.send(Map.of("type", "post", "clientMsgId", "s1", "content", "Yes, it is."));
            buyer.awaitFrame("message", f -> Integer.valueOf(2).equals(f.get("seq")));

            // a duplicate client message id posts once
            buyer.send(Map.of("type", "post", "clientMsgId", "c1", "content", "Is it still available?"));
            Map<String, Object> duplicate = buyer.awaitFrame("ack", f -> "c1".equals(f.get("clientMsgId")) && Boolean.TRUE.equals(f.get("duplicate")));
            assertThat(duplicate).containsEntry("seq", 1);
            await().atMost(WAIT).untilAsserted(() -> get("/api/conversations/" + CONVERSATION + "/messages", AS_MEMBER, BUYER)
                    .expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(2).jsonPath("$[1].content").isEqualTo("Yes, it is."));

            // only the buyer makes offers; the seller's attempt is refused on the socket
            seller.send(Map.of("type", "offer", "clientMsgId", "s2", "amountCents", 90000, "currency", "SEK"));
            assertThat(seller.awaitFrame("error", f -> "s2".equals(f.get("clientMsgId")))).containsEntry("code", "not-buyer").containsEntry("status", 403);
            buyer.send(Map.of("type", "offer", "clientMsgId", "c2", "amountCents", 90000, "currency", "SEK", "content", "Would you take 900?"));
            Map<String, Object> offer = seller.awaitFrame("message", f -> "OFFER".equals(f.get("contentType")));
            assertThat(offer).containsEntry("offerAmountCents", 90000).containsEntry("currency", "SEK");

            seller.send(Map.of("type", "accept-offer"));
            seller.awaitFrame("ack", f -> "accept-offer".equals(f.get("action")));
            Map<String, Object> accepted = buyer.awaitFrame("status", f -> "OFFER_ACCEPTED".equals(f.get("change")));
            assertThat(accepted).containsEntry("amountCents", 90000).containsEntry("messageId", offer.get("messageId"));

            // OfferAcceptedV1 on messaging.v1 with the agreed field names
            await().atMost(WAIT).until(() -> Stubs.publishedHeaders.stream().anyMatch(h -> "messaging.offer-accepted".equals(h.get(PublicEventHeaders.EVENT_TYPE))));
            int index = Stubs.publishedHeaders.indexOf(Stubs.publishedHeaders.stream()
                    .filter(h -> "messaging.offer-accepted".equals(h.get(PublicEventHeaders.EVENT_TYPE))).findFirst().orElseThrow());
            Map<String, Object> payload = Stubs.published.get(index);
            assertThat(payload.keySet()).containsExactlyInAnyOrder("conversationId", "announceId", "buyerId", "sellerId", "amountCents", "currency", "acceptedAt");
            assertThat(payload).containsEntry("conversationId", CONVERSATION).containsEntry("announceId", ANNOUNCE)
                    .containsEntry("buyerId", BUYER).containsEntry("sellerId", SELLER).containsEntry("amountCents", 90000).containsEntry("currency", "SEK");
            assertThat(Stubs.publishedHeaders.get(index)).containsEntry(PublicEventHeaders.EVENT_VERSION, "1").containsEntry(PublicEventHeaders.SUBJECT, "/conversation/" + CONVERSATION);
            assertThat(Stubs.publishedHeaders.stream().filter(h -> "messaging.conversation-opened".equals(h.get(PublicEventHeaders.EVENT_TYPE))).count()).isGreaterThanOrEqualTo(1);

            // the read model: accepted offer, offer message status, unread counts and read markers
            await().atMost(WAIT).untilAsserted(() -> get("/api/conversations/" + CONVERSATION, AS_MEMBER, SELLER).expectStatus().isOk()
                    .expectBody().jsonPath("$.acceptedOffer.amount").isEqualTo(900.0).jsonPath("$.acceptedOffer.currency").isEqualTo("SEK")
                    .jsonPath("$.openOffer").doesNotExist().jsonPath("$.messageCount").isEqualTo(3).jsonPath("$.unread").isEqualTo(3));
            get("/api/conversations/" + CONVERSATION + "/messages?after=2", AS_MEMBER, SELLER).expectStatus().isOk()
                    .expectBody().jsonPath("$.length()").isEqualTo(1).jsonPath("$[0].offerStatus").isEqualTo("ACCEPTED").jsonPath("$[0].offerAmount").isEqualTo(900.0);
            seller.send(Map.of("type", "read", "seq", 3));
            seller.awaitFrame("ack", f -> "read".equals(f.get("action")));
            await().atMost(WAIT).untilAsserted(() -> get("/api/conversations/" + CONVERSATION, AS_MEMBER, SELLER).expectStatus().isOk()
                    .expectBody().jsonPath("$.unread").isEqualTo(0));

            buyer.send(Map.of("type", "nonsense"));
            assertThat(buyer.awaitFrame("error", f -> "nonsense".equals(f.get("action")))).containsEntry("code", "unknown-frame");
        } finally {
            buyer.close();
            seller.close();
        }
        await().atMost(WAIT).until(() -> sessions.sessions(CONVERSATION) == 0);

        // a stranger cannot attach, and nobody can without a principal
        assertThat(new WsClient().connect("/ws/conversations/" + CONVERSATION, AS_MEMBER, STRANGER).awaitClose().getCode())
                .isEqualTo(ConversationWebSocketHandler.NOT_PARTICIPANT);
        assertThat(new WsClient().connect("/ws/conversations/" + CONVERSATION, null, 0).awaitClose().getCode())
                .isEqualTo(ConversationWebSocketHandler.NOT_AUTHENTICATED);
    }

    @Test
    @Order(3)
    void aSecondBuyerCannotPostAndOrderEventsBecomeSystemMessages() {
        client.post().uri("/api/conversations/" + CONVERSATION + "/messages").headers(h -> { h.set(Principals.USER_HEADER, String.valueOf(String.valueOf(STRANGER))); h.set(Principals.TYPE_HEADER, "MEMBER"); })
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"clientMsgId\":\"x1\",\"content\":\"me too\"}")
                .exchange().expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("not-participant");

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String eventId = UUID.randomUUID().toString();
        publishOrderEvent(new OrderPaidV1("o-1", ANNOUNCE, BUYER, SELLER, 90000, "SEK", now), eventId);
        await().atMost(WAIT).untilAsserted(() -> get("/api/conversations/" + CONVERSATION + "/messages?after=3", AS_MEMBER, BUYER)
                .expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].senderType").isEqualTo("SYSTEM").jsonPath("$[0].contentType").isEqualTo("SYSTEM")
                .jsonPath("$[0].content").isEqualTo("Payment of 900.00 SEK received. The seller can now ship."));
        // the same record delivered again posts nothing
        publishOrderEvent(new OrderPaidV1("o-1", ANNOUNCE, BUYER, SELLER, 90000, "SEK", now), eventId);

        // a deal without any chat gets its conversation opened by the order event
        String other = ConversationIds.listing(OTHER_ANNOUNCE, OTHER_BUYER);
        conversations.add(other);
        publishOrderEvent(new OrderAgreedV1("o-2", OTHER_ANNOUNCE, OTHER_BUYER, SELLER, "AUCTION", 120000, 10100, 109900, "SEK", "Bike", now), UUID.randomUUID().toString());
        await().atMost(WAIT).untilAsserted(() -> get("/api/conversations/" + other + "/messages", AS_MEMBER, OTHER_BUYER)
                .expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].content").isEqualTo("Deal agreed at 1200.00 SEK. The buyer can now pay."));
        get("/api/conversations/" + other, AS_MEMBER, SELLER).expectStatus().isOk()
                .expectBody().jsonPath("$.buyerId").isEqualTo(OTHER_BUYER).jsonPath("$.participants.length()").isEqualTo(2);

        await().pollDelay(Duration.ofSeconds(2)).atMost(WAIT).untilAsserted(() -> get("/api/conversations/" + CONVERSATION + "/messages?after=3", AS_MEMBER, BUYER)
                .expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(1));
    }

    @Test
    @Order(4)
    void aSupportConversationWaitsUntilStaffJoins() {
        byte[] body = open(BUYER, "{\"type\":\"SUPPORT\",\"subject\":\"e2e Where is my parcel?\"}").expectStatus().isCreated()
                .expectBody().jsonPath("$.status").isEqualTo("WAITING").jsonPath("$.participants.length()").isEqualTo(1)
                .returnResult().getResponseBody();
        String json = new String(body);
        String id = json.substring(json.indexOf("\"id\":\"") + 6, json.indexOf('"', json.indexOf("\"id\":\"") + 6));
        conversations.add(id);

        await().atMost(WAIT).untilAsserted(() -> get("/api/admin/conversations?status=WAITING", AS_STAFF, STAFF).expectStatus().isOk()
                .expectBody().jsonPath("$.items[?(@.id == '" + id + "')]").exists());
        // the queue needs staff: nobody is 401, a member 403 (what the customer edge forwards)
        client.get().uri("/api/admin/conversations?status=WAITING").exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(Principals.NO_PRINCIPAL);
        get("/api/admin/conversations?status=WAITING", AS_MEMBER, BUYER).expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo(Principals.NOT_STAFF);

        client.post().uri("/api/admin/conversations/" + id + "/join").headers(h -> { h.set(Principals.USER_HEADER, String.valueOf(String.valueOf(STAFF))); h.set(Principals.TYPE_HEADER, "ADMIN"); }).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("OPEN")
                .jsonPath("$.participants[?(@.type == 'ADMIN' && @.id == " + STAFF + ")]").exists();
        await().atMost(WAIT).untilAsserted(() -> get("/api/admin/conversations?status=WAITING", AS_STAFF, STAFF).expectStatus().isOk()
                .expectBody().jsonPath("$.items[?(@.id == '" + id + "')]").doesNotExist());
        await().atMost(WAIT).untilAsserted(() -> get("/api/conversations/" + id, AS_STAFF, STAFF).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("OPEN").jsonPath("$.participants.length()").isEqualTo(2));
        client.post().uri("/api/conversations/" + id + "/messages").headers(h -> { h.set(Principals.USER_HEADER, String.valueOf(String.valueOf(STAFF))); h.set(Principals.TYPE_HEADER, "ADMIN"); })
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"clientMsgId\":\"st1\",\"content\":\"How can I help?\"}")
                .exchange().expectStatus().isCreated().expectBody().jsonPath("$.seq").isEqualTo(1);
        client.post().uri("/api/admin/conversations/" + id + "/close").headers(h -> { h.set(Principals.USER_HEADER, String.valueOf(String.valueOf(STAFF))); h.set(Principals.TYPE_HEADER, "ADMIN"); }).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("CLOSED");
    }

    @Test
    @Order(5)
    void bulletinsReachTheStaffSocketsOfTheirClientType() {
        WsClient desktop = new WsClient().connect("/ws/staff?clientType=desktop", AS_STAFF, STAFF);
        WsClient mobile = new WsClient().connect("/ws/staff?clientType=mobile-web", AS_STAFF, OTHER_STAFF);
        await().atMost(WAIT).until(() -> sessions.staffSessions() == 2);
        try {
            byte[] body = client.post().uri("/api/admin/bulletins").headers(h -> { h.set(Principals.USER_HEADER, String.valueOf(String.valueOf(STAFF))); h.set(Principals.TYPE_HEADER, "ADMIN"); })
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"audience\":\"STAFF\",\"clientType\":\"desktop\",\"content\":\"e2e Queue is long, please pick up\"}")
                    .exchange().expectStatus().isCreated().expectBody().jsonPath("$.id").isNumber().returnResult().getResponseBody();
            bulletins.add(Integer.valueOf(parse(new String(body)).get("id").toString()));
            assertThat(desktop.awaitFrame("bulletin", f -> "e2e Queue is long, please pick up".equals(f.get("content")))).containsEntry("clientType", "desktop");
            assertThat(mobile.received).noneMatch(f -> "bulletin".equals(f.get("type")));

            body = client.post().uri("/api/admin/bulletins").headers(h -> { h.set(Principals.USER_HEADER, String.valueOf(String.valueOf(STAFF))); h.set(Principals.TYPE_HEADER, "ADMIN"); })
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"content\":\"e2e All hands\"}")
                    .exchange().expectStatus().isCreated().expectBody().returnResult().getResponseBody();
            bulletins.add(Integer.valueOf(parse(new String(body)).get("id").toString()));
            desktop.awaitFrame("bulletin", f -> "e2e All hands".equals(f.get("content")));
            mobile.awaitFrame("bulletin", f -> "e2e All hands".equals(f.get("content")));

            get("/api/admin/bulletins", AS_STAFF, STAFF).expectStatus().isOk()
                    .expectBody().jsonPath("$[?(@.content == 'e2e All hands')]").exists();
        } finally {
            desktop.close();
            mobile.close();
        }
        assertThat(new WsClient().connect("/ws/staff", null, 0).awaitClose().getCode()).isEqualTo(ConversationWebSocketHandler.NOT_AUTHENTICATED);
    }

    /**
     * The handshake authorises against the read model, which the projection writes a little after the command
     * answered. The client does not wait: the handshake looks the conversation up again until it is there.
     */
    @Test
    @Order(6)
    void aSocketAttachesRightAfterTheConversationIsOpened() {
        conversations.add(RACE);
        open(BUYER, "{\"type\":\"LISTING\",\"announceId\":\"" + RACE_ANNOUNCE + "\"}").expectStatus().isCreated()
                .expectBody().jsonPath("$.id").isEqualTo(RACE);
        WsClient buyer = new WsClient().connect("/ws/conversations/" + RACE, AS_MEMBER, BUYER);
        try {
            buyer.send(Map.of("type", "ping"));
            buyer.awaitFrame("pong", f -> true);
            assertThat(sessions.sessions(RACE)).isEqualTo(1);
        } finally {
            buyer.close();
        }
        await().atMost(WAIT).until(() -> sessions.sessions(RACE) == 0);

        // a conversation that never appears is still refused, once the look-ups are used up
        assertThat(new WsClient().connect("/ws/conversations/" + UUID.randomUUID(), AS_MEMBER, BUYER).awaitClose().getCode())
                .isEqualTo(ConversationWebSocketHandler.NOT_PARTICIPANT);
    }

    /**
     * Commands handled at the same moment on one conversation read the same state; the event store takes the
     * first write and refuses the others. The refused ones are dispatched again, so nobody's message is lost.
     */
    @Test
    @Order(7)
    void postsRacingInOneConversationAreAllAcknowledged() {
        WsClient buyer = new WsClient().connect("/ws/conversations/" + RACE, AS_MEMBER, BUYER);
        WsClient seller = new WsClient().connect("/ws/conversations/" + RACE, AS_MEMBER, SELLER);
        await().atMost(WAIT).until(() -> sessions.sessions(RACE) == 2);
        int rounds = 10;
        try {
            // buyer and seller post at the same moment, round after round
            Set<Object> seqs = new java.util.HashSet<>();
            for (int round = 1; round <= rounds; round++) {
                buyer.send(Map.of("type", "post", "clientMsgId", "rb" + round, "content", "buyer " + round));
                seller.send(Map.of("type", "post", "clientMsgId", "rs" + round, "content", "seller " + round));
                for (Map<String, Object> answer : List.of(buyer.awaitAnswer("rb" + round), seller.awaitAnswer("rs" + round))) {
                    assertThat(answer).as("round %d", round).containsEntry("type", "ack").containsEntry("duplicate", false);
                    seqs.add(answer.get("seq"));
                }
            }
            assertThat(seqs).as("every post got its own place in the conversation")
                    .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 2 * rounds).boxed().collect(Collectors.toList()));
        } finally {
            buyer.close();
            seller.close();
        }
        await().atMost(WAIT).until(() -> sessions.sessions(RACE) == 0);

        // four writers at once over REST: each can lose to the three others, which the three retries absorb
        WebClient http = WebClient.builder().baseUrl("http://localhost:" + port).filter(EdgeRequests.signing()).build();
        List<Integer> statuses = Flux.range(1, 4).flatMap(i -> http.post().uri("/api/conversations/" + RACE + "/messages")
                .headers(h -> { h.set(Principals.USER_HEADER, String.valueOf(String.valueOf(i % 2 == 0 ? BUYER : SELLER))); h.set(Principals.TYPE_HEADER, "MEMBER"); }).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"clientMsgId\":\"rr" + i + "\",\"content\":\"burst " + i + "\"}")
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value())), 4).collectList().block(WAIT);
        assertThat(statuses).containsExactly(201, 201, 201, 201);

        await().atMost(WAIT).untilAsserted(() -> get("/api/conversations/" + RACE, AS_MEMBER, BUYER).expectStatus().isOk()
                .expectBody().jsonPath("$.messageCount").isEqualTo(2 * rounds + 4).jsonPath("$.lastMessageSeq").isEqualTo(2 * rounds + 4));
    }

    /** The meters of doc/messaging-architecture.md section 5 saw the traffic of the tests above. */
    @Test
    @Order(8)
    void metersCoverCommandsProjectionsAndTheLiveFanOut() {
        assertThat(timer(MessagingMeters.COMMAND_LATENCY, MessagingMeters.TAG_COMMAND, "PostMessage", MessagingMeters.TAG_OUTCOME, MessagingMeters.OUTCOME_OK).count())
                .isGreaterThan(0);
        assertThat(timer(MessagingMeters.COMMAND_LATENCY, MessagingMeters.TAG_COMMAND, "PostMessage", MessagingMeters.TAG_OUTCOME, MessagingMeters.OUTCOME_REJECTED).count())
                .as("the stranger's post was rejected").isGreaterThan(0);
        assertThat(meters.find(MessagingMeters.COMMAND_LATENCY).tag(MessagingMeters.TAG_COMMAND, "PostMessage")
                .tag(MessagingMeters.TAG_OUTCOME, MessagingMeters.OUTCOME_ERROR).timers())
                .as("no post was lost to a concurrent write").allSatisfy(t -> assertThat(t.count()).isZero());
        assertThat(MessagingMeters.commandRetries(meters, "PostMessage").count())
                .as("the racing posts collided and were dispatched again").isGreaterThan(0);
        for (String group : List.of(ConversationReadModelProjection.GROUP, MessagingRelays.PUBLIC_GROUP, MessagingRelays.LIVE_GROUP)) {
            assertThat(timer(MessagingMeters.PROJECTION_DELAY, MessagingMeters.TAG_GROUP, group).count()).as(group).isGreaterThan(0);
            assertThat(timer(MessagingMeters.PROJECTION_HANDLER, MessagingMeters.TAG_GROUP, group, MessagingMeters.TAG_OUTCOME, MessagingMeters.OUTCOME_OK).count())
                    .as(group).isGreaterThan(0);
        }
        Timer live = timer(MessagingMeters.LIVE_LATENCY, MessagingMeters.TAG_RECORD, "message");
        assertThat(live.count()).isGreaterThan(0);
        assertThat(live.max(java.util.concurrent.TimeUnit.SECONDS)).isLessThan(WAIT.toSeconds());
        assertThat(timer(MessagingMeters.LIVE_LATENCY, MessagingMeters.TAG_RECORD, "bulletin").count()).isGreaterThan(0);
        assertThat(meters.get(MessagingMeters.LIVE_DELIVERED).tag(MessagingMeters.TAG_RECORD, "message").counter().count())
                .as("both sockets of the listing conversation received the messages").isGreaterThanOrEqualTo(2);
        assertThat(meters.get(MessagingMeters.SESSIONS).gauge().value()).as("every socket detached").isZero();
        assertThat(meters.get(MessagingMeters.STAFF_SESSIONS).gauge().value()).isZero();
        assertThat(meters.get(MessagingMeters.SINK_BUFFERED).gauge().value()).isZero();
        assertThat(meters.find(MessagingMeters.SINK_DROPPED).counters()).allSatisfy(c -> assertThat(c.count()).isZero());
    }

    Timer timer(String name, String... tags) {
        return meters.get(name).tags(tags).timer();
    }
}
