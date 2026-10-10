package org.linlinjava.announce.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscribers;
import org.linlinjava.announce.cqrs.security.Callers;
import org.linlinjava.announce.cqrs.security.ServiceTokenWebFilter;
import org.linlinjava.announce.cqrs.security.ServiceTokens;
import org.linlinjava.announce.cqrs.test.EdgeRequests;
import org.linlinjava.announce.cqrs.test.PublicEventTestProducer;
import org.linlinjava.announce.cqrs.test.containers.CqrsContainers;
import org.linlinjava.announce.cqrs.test.containers.EventSourcingDbContainer;
import org.linlinjava.announce.db.dao.reactive.AnnounceAuctionSellerMapper;
import org.linlinjava.announce.db.dao.reactive.AnnounceOrderSellerMapper;
import org.linlinjava.announce.goods.api.AnnouncePublishedV1;
import org.linlinjava.announce.goods.api.AnnounceTopics;
import org.linlinjava.announce.messaging.api.OfferAcceptedV1;
import org.linlinjava.announce.order.api.OrderAgreedV1;
import org.linlinjava.announce.order.api.OrderPaidV1;
import org.linlinjava.announce.order.api.OrderRefundedV1;
import org.linlinjava.announce.order.api.OrderSettledV1;
import org.linlinjava.announce.order.api.OrderTopics;
import org.linlinjava.announce.order.application.OrderService;
import org.linlinjava.announce.payment.infrastructure.provider.WebhookSignature;
import org.linlinjava.announce.shipping.infrastructure.carrier.CarrierWebhookSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole module with the fakes, on EventSourcingDB, Kafka and MySQL containers (schema from db-reactive/sql).
 * The other services are stood in for by a test producer on {@code announce.v1} and {@code messaging.v1}; a probe
 * on {@code order.v1} observes what the module publishes. Time moves through a mutable clock and the jobs are run
 * by hand. Scenarios (a) to (d) of the acceptance line in CLAUDE.md.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // as in production: only requests signed by an edge are served (the test profile alone says off)
        properties = {EdgeRequests.ENFORCE, EdgeRequests.SECRET_PROPERTY})
@ActiveProfiles("test")
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderModuleEndToEndTest {

    static final String SQL = "../db-reactive/sql/";
    static final Duration WAIT = Duration.ofSeconds(60);
    static final Instant START = Instant.parse("2026-09-26T10:00:00Z");
    static final MutableClock CLOCK = new MutableClock(START, ZoneId.systemDefault());
    static final int SELLER = 7;
    static final int ALICE = 9;
    static final int BOB = 10;
    static final int ADMIN = 1;
    static final String PAYMENT_SECRET = "fake-webhook-secret";
    static final String CARRIER_SECRET = "fake-carrier-secret";

    @Container
    static final EventSourcingDbContainer esdb = CqrsContainers.eventSourcingDb();

    @Container
    static final KafkaContainer kafka = CqrsContainers.kafka();

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>(CqrsContainers.MYSQL_IMAGE)
            .withDatabaseName("announce_reactive_db").withUsername("announce").withPassword("announce")
            .withCopyFileToContainer(MountableFile.forHostPath(SQL + "announce_table.sql"), "/docker-entrypoint-initdb.d/01_tables.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(SQL + "announce_auction_chat.sql"), "/docker-entrypoint-initdb.d/02_auction_chat.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(SQL + "announce_order.sql"), "/docker-entrypoint-initdb.d/03_order.sql")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        CqrsContainers.register(registry, esdb, kafka, mysql);
        registry.add("spring.r2dbc.mybatis.r2dbc-url", () -> CqrsContainers.r2dbcUrl(mysql));
        registry.add("spring.r2dbc.mybatis.username", mysql::getUsername);
        registry.add("spring.r2dbc.mybatis.password", mysql::getPassword);
    }

    @TestConfiguration
    static class Probes {
        static final List<Object> orderEvents = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }

        @Bean
        Consumer<Message<byte[]>> orderProbe(PublicEventSubscribers subscribers) {
            return subscribers.forTopic("order-probe")
                    .on(OrderAgreedV1.class, (e, h) -> Mono.fromRunnable(() -> orderEvents.add(e)))
                    .on(OrderPaidV1.class, (e, h) -> Mono.fromRunnable(() -> orderEvents.add(e)))
                    .on(OrderSettledV1.class, (e, h) -> Mono.fromRunnable(() -> orderEvents.add(e)))
                    .on(OrderRefundedV1.class, (e, h) -> Mono.fromRunnable(() -> orderEvents.add(e)))
                    .build();
        }

        @Bean
        PublicEventTestProducer otherServices(StreamBridge streamBridge, ObjectMapper objectMapper) {
            return new PublicEventTestProducer(streamBridge, objectMapper, "e2e-test");
        }

        /** The autowired client stands for an edge: each of its requests carries a fresh X-Service-Token. */
        @Bean
        WebTestClientBuilderCustomizer edgeSignedClient() {
            return EdgeRequests.signedWebTestClient();
        }
    }

    @Autowired WebTestClient client;
    @LocalServerPort int port;
    @Autowired PublicEventTestProducer others;
    @Autowired OrderService orders;   // the jobs are disabled in the test profile; their passes are run by hand
    @Autowired AnnounceAuctionSellerMapper auctionSellers;
    @Autowired AnnounceOrderSellerMapper orderSellers;

    static String auctionAnnounce = UUID.randomUUID().toString();
    static String fixedAnnounce = UUID.randomUUID().toString();
    static String offerAnnounce = UUID.randomUUID().toString();
    static String lateAnnounce = UUID.randomUUID().toString();
    static String auctionOrderId;
    static String fixedOrderId;

    // ------------------------------------------------------------------ helpers

    static LocalDateTime now() {
        return LocalDateTime.now(CLOCK);
    }

    private void publish(String announceId, String saleType, long priceCents) {
        others.send(AnnounceTopics.ANNOUNCE_V1, "/announce/" + announceId, new AnnouncePublishedV1(announceId, "GOODS", SELLER, "Bike " + announceId,
                "http://x/1.jpg", priceCents, "DKK", saleType, 1005007, now(), now().plusDays(60)));
    }

    /** What the customer edge forwards for a signed-in member. */
    private WebTestClient.ResponseSpec as(int user, String method, String uri, Object body) {
        return with(Callers.MEMBER, user, method, uri, body);
    }

    /** What the admin edge forwards for staff member {@link #ADMIN}. */
    private WebTestClient.ResponseSpec staff(String method, String uri, Object body) {
        return with(Callers.ADMIN, ADMIN, method, uri, body);
    }

    private WebTestClient.ResponseSpec with(String type, int id, String method, String uri, Object body) {
        WebTestClient.RequestBodySpec spec = client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(uri)
                .header(Callers.USER_HEADER, String.valueOf(id)).header(Callers.TYPE_HEADER, type);
        return (body == null ? spec : spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body)).exchange();
    }

    private void paymentWebhook(String orderId, String kind, String eventId) {
        String body = "{\"providerEventId\":\"" + eventId + "\",\"kind\":\"" + kind + "\",\"orderId\":\"" + orderId + "\",\"ref\":\"" + kind.toLowerCase() + "-" + eventId + "\"}";
        client.post().uri("/api/payments/webhook/fake").contentType(MediaType.APPLICATION_JSON)
                .header("X-Fake-Signature", WebhookSignature.sign(PAYMENT_SECRET, body)).bodyValue(body).exchange().expectStatus().isNoContent();
    }

    private void carrierWebhook(String orderId, String kind, String eventId) {
        String body = "{\"carrierEventId\":\"" + eventId + "\",\"kind\":\"" + kind + "\",\"orderId\":\"" + orderId + "\",\"at\":\"" + now() + "\"}";
        client.post().uri("/api/shipping/webhook/fake").contentType(MediaType.APPLICATION_JSON)
                .header("X-Fake-Signature", CarrierWebhookSignature.sign(CARRIER_SECRET, body)).bodyValue(body).exchange().expectStatus().isNoContent();
    }

    private void awaitOrderStatus(String orderId, int user, String status) {
        await().atMost(WAIT).untilAsserted(() -> as(user, "GET", "/api/orders/" + orderId, null).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo(status));
    }

    private static <T> List<T> observed(Class<T> type, String orderId) {
        return Probes.orderEvents.stream().filter(type::isInstance).map(type::cast)
                .filter(e -> orderIdOf(e).equals(orderId)).toList();
    }

    private static String orderIdOf(Object e) {
        return switch (e) {
            case OrderAgreedV1 a -> a.orderId();
            case OrderPaidV1 p -> p.orderId();
            case OrderSettledV1 s -> s.orderId();
            case OrderRefundedV1 r -> r.orderId();
            default -> "";
        };
    }

    // ------------------------------------------------------------------ scenarios

    @Test
    @Order(0)
    void onlyRequestsThatPassedAnEdgeAreServed() {
        WebTestClient direct = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        direct.get().uri("/api/orders?role=buyer").header("X-User-Id", String.valueOf(ALICE)).exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.type").isEqualTo(ServiceTokenWebFilter.PROBLEM_TYPE_MISSING);
        direct.post().uri("/api/admin/auctions/close-due").header(Callers.USER_HEADER, String.valueOf(ADMIN)).header(Callers.TYPE_HEADER, Callers.ADMIN).exchange()
                .expectStatus().isUnauthorized();
        direct.post().uri("/api/payments/webhook/fake").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isUnauthorized();
        direct.get().uri("/api/orders?role=buyer").header("X-User-Id", String.valueOf(ALICE))
                .header(ServiceTokens.HEADER, ServiceTokens.mint("gateway-api", "guessed", Instant.now().getEpochSecond())).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.type").isEqualTo(ServiceTokenWebFilter.PROBLEM_TYPE_INVALID);
        // health checks reach the service directly
        direct.get().uri("/actuator/health").exchange().expectStatus().value(status -> assertThat(status).isNotEqualTo(401));

        as(ALICE, "GET", "/api/orders?role=buyer", null).expectStatus().isOk();
        // one caller convention: a member is refused on the staff routes and staff on the member routes
        as(ALICE, "POST", "/api/admin/auctions/close-due", null).expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo(Callers.NOT_STAFF);
        staff("GET", "/api/orders?role=buyer", null).expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo(Callers.NOT_A_MEMBER);
        client.get().uri("/api/orders?role=buyer").exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo(Callers.NO_PRINCIPAL);
        // the former staff header is not an identity
        client.post().uri("/api/admin/auctions/close-due").header("X-Admin-Id", String.valueOf(ADMIN)).exchange().expectStatus().isUnauthorized();
    }

    @Test
    @Order(1)
    void theSellerOnboardsAndBothContextsLearnThatTheyCanBePaid() {
        as(SELLER, "POST", "/api/sellers/me/account", null).expectStatus().isCreated()
                .expectBody().jsonPath("$.status").isEqualTo("VERIFIED").jsonPath("$.payoutsEnabled").isEqualTo(true);
        await().atMost(WAIT).untilAsserted(() -> as(SELLER, "GET", "/api/sellers/me/account", null).expectStatus().isOk()
                .expectBody().jsonPath("$.payoutsEnabled").isEqualTo(true));
        as(SELLER, "POST", "/api/sellers/me/account", null).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("already-onboarded");
        // payment.v1 told the auction and order contexts that the seller can be paid
        await().atMost(WAIT).untilAsserted(() -> assertThat(auctionSellers.selectByPrimaryKey(SELLER).map(r -> r.getPayoutsEnabled()).block(WAIT)).isTrue());
        await().atMost(WAIT).untilAsserted(() -> assertThat(orderSellers.selectByPrimaryKey(SELLER).map(r -> r.getPayoutsEnabled()).block(WAIT)).isTrue());
        // the webhook with a bad signature is refused
        client.post().uri("/api/payments/webhook/fake").contentType(MediaType.APPLICATION_JSON).header("X-Fake-Signature", "nope")
                .bodyValue("{\"providerEventId\":\"x\",\"kind\":\"CONFIRMED\",\"orderId\":\"y\"}").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @Order(2)
    void auctionToSettlement() {
        publish(auctionAnnounce, "AUCTION", 10000);
        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/auctions/" + auctionAnnounce).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.auction.status").isEqualTo("OPEN").jsonPath("$.auction.sellerId").isEqualTo(SELLER));

        // a bid from the seller and a bid below the increment are rejected; two valid bids
        as(SELLER, "POST", "/api/auctions/" + auctionAnnounce + "/bids", "{\"amountCents\":10000}").expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("seller-cannot-bid");
        as(ALICE, "POST", "/api/auctions/" + auctionAnnounce + "/bids", "{\"amountCents\":10000}").expectStatus().isCreated();
        as(BOB, "POST", "/api/auctions/" + auctionAnnounce + "/bids", "{\"amountCents\":10500}").expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("bid-below-minimum");
        as(BOB, "POST", "/api/auctions/" + auctionAnnounce + "/bids", "{\"amountCents\":12000}").expectStatus().isCreated()
                .expectBody().jsonPath("$.bids.length()").isEqualTo(2);
        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/auctions/" + auctionAnnounce).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.auction.bidCount").isEqualTo(2).jsonPath("$.auction.currentPrice").isEqualTo(120.0)
                .jsonPath("$.bids[0].status").isEqualTo("ACTIVE").jsonPath("$.bids[1].status").isEqualTo("OUTBID"));

        // the end passes: the closing job closes it won, the order context opens the deal with the winner
        CLOCK.advance(Duration.ofDays(8));
        staff("POST", "/api/admin/auctions/close-due", null).expectStatus().isOk().expectBody().jsonPath("$.closed").isEqualTo(1);
        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/auctions/" + auctionAnnounce).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.auction.status").isEqualTo("CLOSED_WON").jsonPath("$.auction.winnerId").isEqualTo(BOB));
        await().atMost(WAIT).untilAsserted(() -> assertThat(Probes.orderEvents).filteredOn(e -> e instanceof OrderAgreedV1 a && a.announceId().equals(auctionAnnounce)).hasSize(1));
        OrderAgreedV1 agreed = Probes.orderEvents.stream().filter(e -> e instanceof OrderAgreedV1 a && a.announceId().equals(auctionAnnounce)).map(OrderAgreedV1.class::cast).findFirst().orElseThrow();
        auctionOrderId = agreed.orderId();
        assertThat(agreed.buyerId()).isEqualTo(BOB);
        assertThat(agreed.priceCents()).isEqualTo(12000);
        assertThat(agreed.feeCents()).isEqualTo(1460);      // 8% of 12000 + 500
        assertThat(agreed.payoutCents()).isEqualTo(10540);
        assertThat(agreed.door()).isEqualTo("AUCTION");

        // the payment context opened an intent: the order is PAYMENT_PENDING and the buyer sees the client secret
        awaitOrderStatus(auctionOrderId, BOB, "PAYMENT_PENDING");
        await().atMost(WAIT).untilAsserted(() -> as(BOB, "GET", "/api/orders/" + auctionOrderId + "/payment", null).expectStatus().isOk()
                .expectBody().jsonPath("$.clientSecret").isNotEmpty().jsonPath("$.status").isEqualTo("INTENT_CREATED"));

        // the provider confirms: PAID, OrderPaidV1 observed; the same webhook again is ignored
        paymentWebhook(auctionOrderId, "CONFIRMED", "evt-a-1");
        awaitOrderStatus(auctionOrderId, BOB, "PAID");
        await().atMost(WAIT).untilAsserted(() -> assertThat(observed(OrderPaidV1.class, auctionOrderId)).hasSize(1));
        paymentWebhook(auctionOrderId, "CONFIRMED", "evt-a-1");
        paymentWebhook(auctionOrderId, "CONFIRMED", "evt-a-1-bis");

        // payout refused before delivery (release on DELIVERY): the seller books, the carrier dispatches
        await().atMost(WAIT).untilAsserted(() -> as(SELLER, "GET", "/api/orders/" + auctionOrderId + "/shipment", null).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("OPEN"));
        as(BOB, "POST", "/api/orders/" + auctionOrderId + "/shipment", "{\"service\":\"standard\",\"parcelSize\":\"M\"}").expectStatus().isForbidden();
        as(SELLER, "POST", "/api/orders/" + auctionOrderId + "/shipment", "{\"service\":\"standard\",\"parcelSize\":\"M\",\"fromAddress\":\"A\",\"toAddress\":\"B\"}")
                .expectStatus().isCreated().expectBody().jsonPath("$.trackingNumber").isNotEmpty().jsonPath("$.labelUrl").isNotEmpty();
        carrierWebhook(auctionOrderId, "DISPATCHED", "c-a-1");
        awaitOrderStatus(auctionOrderId, BOB, "SHIPPED");
        staff("POST", "/api/admin/orders/" + auctionOrderId + "/release", null).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("not-delivered");

        // delivered, grace elapses (clock), the release job asks the payout, the provider pays out: SETTLED
        carrierWebhook(auctionOrderId, "DELIVERED", "c-a-2");
        awaitOrderStatus(auctionOrderId, BOB, "DELIVERED");
        orders.releaseDue(200).block(WAIT);
        as(BOB, "GET", "/api/orders/" + auctionOrderId, null).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("DELIVERED");   // grace still running
        CLOCK.advance(Duration.ofDays(4));
        orders.releaseDue(200).block(WAIT);
        awaitOrderStatus(auctionOrderId, BOB, "RELEASING");
        await().atMost(WAIT).untilAsserted(() -> as(BOB, "GET", "/api/orders/" + auctionOrderId + "/payment", null).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("RELEASING").jsonPath("$.payoutRef").isNotEmpty());
        paymentWebhook(auctionOrderId, "PAYOUT_SENT", "evt-a-2");
        awaitOrderStatus(auctionOrderId, BOB, "SETTLED");
        await().atMost(WAIT).untilAsserted(() -> assertThat(observed(OrderSettledV1.class, auctionOrderId)).hasSize(1));
        assertThat(observed(OrderSettledV1.class, auctionOrderId).get(0).payoutCents()).isEqualTo(10540);
        assertThat(observed(OrderPaidV1.class, auctionOrderId)).hasSize(1);
    }

    @Test
    @Order(3)
    void fixedPriceWithReceiptConfirmedBeforeTheGrace() {
        publish(fixedAnnounce, "FIXED_PRICE", 5000);
        await().atMost(WAIT).untilAsserted(() -> as(ALICE, "POST", "/api/orders", "{\"announceId\":\"" + fixedAnnounce + "\"}").expectStatus().isCreated()
                .expectBody().jsonPath("$.status").isEqualTo("PROPOSED").jsonPath("$.sellerId").isEqualTo(SELLER));
        fixedOrderId = as(ALICE, "GET", "/api/orders?role=buyer", null).expectStatus().isOk().expectBodyList(java.util.Map.class).returnResult().getResponseBody()
                .stream().filter(o -> fixedAnnounce.equals(o.get("announceId"))).map(o -> (String) o.get("id")).findFirst().orElse(null);
        if (fixedOrderId == null) {
            await().atMost(WAIT).untilAsserted(() -> assertThat(as(ALICE, "GET", "/api/orders?role=buyer", null).expectStatus().isOk()
                    .expectBodyList(java.util.Map.class).returnResult().getResponseBody()).anyMatch(o -> fixedAnnounce.equals(o.get("announceId"))));
            fixedOrderId = as(ALICE, "GET", "/api/orders?role=buyer", null).expectBodyList(java.util.Map.class).returnResult().getResponseBody()
                    .stream().filter(o -> fixedAnnounce.equals(o.get("announceId"))).map(o -> (String) o.get("id")).findFirst().orElseThrow();
        }
        as(ALICE, "POST", "/api/orders/" + fixedOrderId + "/accept", null).expectStatus().isForbidden();
        as(SELLER, "POST", "/api/orders/" + fixedOrderId + "/accept", null).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("AGREED").jsonPath("$.feeCents").isEqualTo(900).jsonPath("$.payoutCents").isEqualTo(4100);
        awaitOrderStatus(fixedOrderId, ALICE, "PAYMENT_PENDING");
        paymentWebhook(fixedOrderId, "CONFIRMED", "evt-f-1");
        awaitOrderStatus(fixedOrderId, ALICE, "PAID");
        await().atMost(WAIT).untilAsserted(() -> as(SELLER, "GET", "/api/orders/" + fixedOrderId + "/shipment", null).expectStatus().isOk());
        as(SELLER, "POST", "/api/orders/" + fixedOrderId + "/shipment", "{\"service\":\"standard\",\"parcelSize\":\"S\"}").expectStatus().isCreated();
        carrierWebhook(fixedOrderId, "DISPATCHED", "c-f-1");
        carrierWebhook(fixedOrderId, "DELIVERED", "c-f-2");
        awaitOrderStatus(fixedOrderId, ALICE, "DELIVERED");
        // the buyer confirms before the grace: released at once
        as(ALICE, "POST", "/api/orders/" + fixedOrderId + "/confirm-receipt", null).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("RELEASING");
        await().atMost(WAIT).untilAsserted(() -> as(ALICE, "GET", "/api/orders/" + fixedOrderId + "/payment", null).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("RELEASING"));
        paymentWebhook(fixedOrderId, "PAYOUT_SENT", "evt-f-2");
        awaitOrderStatus(fixedOrderId, ALICE, "SETTLED");
        await().atMost(WAIT).untilAsserted(() -> assertThat(observed(OrderSettledV1.class, fixedOrderId)).hasSize(1));
    }

    @Test
    @Order(4)
    void anAcceptedChatOfferOpensTheDealAtTheOfferAmount() {
        publish(offerAnnounce, "FIXED_PRICE", 8000);
        String conversation = UUID.randomUUID().toString();
        others.send(OrderTopics.MESSAGING_V1, "/conversation/" + conversation, new OfferAcceptedV1(conversation, offerAnnounce, ALICE, SELLER, 6500, "DKK", now()));
        await().atMost(WAIT).untilAsserted(() -> assertThat(Probes.orderEvents).filteredOn(e -> e instanceof OrderAgreedV1 a && a.announceId().equals(offerAnnounce)).hasSize(1));
        OrderAgreedV1 agreed = Probes.orderEvents.stream().filter(e -> e instanceof OrderAgreedV1 a && a.announceId().equals(offerAnnounce)).map(OrderAgreedV1.class::cast).findFirst().orElseThrow();
        assertThat(agreed.door()).isEqualTo("OFFER");
        assertThat(agreed.priceCents()).isEqualTo(6500);
        assertThat(agreed.buyerId()).isEqualTo(ALICE);
        awaitOrderStatus(agreed.orderId(), ALICE, "PAYMENT_PENDING");
        // the same offer event again (redelivery) creates no second order
        others.send(OrderTopics.MESSAGING_V1, "/conversation/" + conversation, new OfferAcceptedV1(conversation, offerAnnounce, ALICE, SELLER, 6500, "DKK", now()));
        as(ALICE, "POST", "/api/orders/" + agreed.orderId() + "/cancel", "{\"reason\":\"changed my mind\"}").expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("CANCELLED");
        awaitOrderStatus(agreed.orderId(), ALICE, "CANCELLED");
        assertThat(Probes.orderEvents).filteredOn(e -> e instanceof OrderAgreedV1 a && a.announceId().equals(offerAnnounce)).hasSize(1);
    }

    @Test
    @Order(5)
    void theShipWindowExpiryRefundsTheBuyerAndADisputeBlocksThePayout() {
        publish(lateAnnounce, "FIXED_PRICE", 3000);
        await().atMost(WAIT).untilAsserted(() -> as(BOB, "POST", "/api/orders", "{\"announceId\":\"" + lateAnnounce + "\"}").expectStatus().isCreated());
        await().atMost(WAIT).untilAsserted(() -> assertThat(as(BOB, "GET", "/api/orders?role=buyer", null).expectBodyList(java.util.Map.class).returnResult().getResponseBody())
                .anyMatch(o -> lateAnnounce.equals(o.get("announceId"))));
        String orderId = as(BOB, "GET", "/api/orders?role=buyer", null).expectBodyList(java.util.Map.class).returnResult().getResponseBody()
                .stream().filter(o -> lateAnnounce.equals(o.get("announceId"))).map(o -> (String) o.get("id")).findFirst().orElseThrow();
        as(SELLER, "POST", "/api/orders/" + orderId + "/accept", null).expectStatus().isOk();
        awaitOrderStatus(orderId, BOB, "PAYMENT_PENDING");
        paymentWebhook(orderId, "CONFIRMED", "evt-l-1");
        awaitOrderStatus(orderId, BOB, "PAID");

        // a dispute blocks the payout even for staff
        as(BOB, "POST", "/api/orders/" + orderId + "/dispute", "{\"reason\":\"seller silent\"}").expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("DISPUTED");
        staff("POST", "/api/admin/orders/" + orderId + "/release", null).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("dispute-open");
        staff("POST", "/api/admin/orders/" + orderId + "/resolve-dispute", "{\"resolution\":\"REFUND\",\"note\":\"never shipped\"}").expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("REFUNDING");
        await().atMost(WAIT).untilAsserted(() -> as(BOB, "GET", "/api/orders/" + orderId + "/payment", null).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("REFUNDING").jsonPath("$.refundRef").isNotEmpty());
        paymentWebhook(orderId, "REFUND_SENT", "evt-l-2");
        awaitOrderStatus(orderId, BOB, "REFUNDED");
        await().atMost(WAIT).untilAsserted(() -> assertThat(observed(OrderRefundedV1.class, orderId)).hasSize(1));

        // and the ship window itself: a paid order nobody ships is refunded by the job
        String late2 = UUID.randomUUID().toString();
        publish(late2, "FIXED_PRICE", 2000);
        await().atMost(WAIT).untilAsserted(() -> as(ALICE, "POST", "/api/orders", "{\"announceId\":\"" + late2 + "\"}").expectStatus().isCreated());
        await().atMost(WAIT).untilAsserted(() -> assertThat(as(ALICE, "GET", "/api/orders?role=buyer", null).expectBodyList(java.util.Map.class).returnResult().getResponseBody())
                .anyMatch(o -> late2.equals(o.get("announceId"))));
        String order2 = as(ALICE, "GET", "/api/orders?role=buyer", null).expectBodyList(java.util.Map.class).returnResult().getResponseBody()
                .stream().filter(o -> late2.equals(o.get("announceId"))).map(o -> (String) o.get("id")).findFirst().orElseThrow();
        as(SELLER, "POST", "/api/orders/" + order2 + "/accept", null).expectStatus().isOk();
        awaitOrderStatus(order2, ALICE, "PAYMENT_PENDING");
        paymentWebhook(order2, "CONFIRMED", "evt-l2-1");
        awaitOrderStatus(order2, ALICE, "PAID");
        orders.expireShipWindows(200).block(WAIT);
        as(ALICE, "GET", "/api/orders/" + order2, null).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("PAID");   // window still open
        CLOCK.advance(Duration.ofDays(8));
        orders.expireShipWindows(200).block(WAIT);
        awaitOrderStatus(order2, ALICE, "REFUNDING");
        await().atMost(WAIT).untilAsserted(() -> as(ALICE, "GET", "/api/orders/" + order2 + "/payment", null).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("REFUNDING"));
        paymentWebhook(order2, "REFUND_SENT", "evt-l2-2");
        awaitOrderStatus(order2, ALICE, "REFUNDED");
        await().atMost(WAIT).untilAsserted(() -> assertThat(observed(OrderRefundedV1.class, order2)).hasSize(1));
        assertThat(observed(OrderRefundedV1.class, order2).get(0).amountCents()).isEqualTo(2000);
        // the shipment that was never booked is cancelled with the deal
        await().atMost(WAIT).untilAsserted(() -> as(SELLER, "GET", "/api/orders/" + order2 + "/shipment", null).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("CANCELLED"));
        assertThat(now()).isAfter(LocalDateTime.ofInstant(START, ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS));
    }
}
