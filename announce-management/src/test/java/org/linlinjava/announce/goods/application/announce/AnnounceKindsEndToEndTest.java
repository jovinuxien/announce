package org.linlinjava.announce.goods.application.announce;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.linlinjava.announce.cqrs.messaging.PublicEventSubscribers;
import org.linlinjava.announce.cqrs.test.EdgeRequests;
import org.linlinjava.announce.cqrs.test.PublicEventTestProducer;
import org.linlinjava.announce.cqrs.test.containers.CqrsContainers;
import org.linlinjava.announce.cqrs.test.containers.EventSourcingDbContainer;
import org.linlinjava.announce.search.RecordingSearchIndexer;
import org.linlinjava.announce.goods.api.AnnouncePublishedV1;
import org.linlinjava.announce.goods.api.AnnounceSoldV1;
import org.linlinjava.announce.cqrs.security.ServiceTokenWebFilter;
import org.linlinjava.announce.cqrs.security.ServiceTokens;
import org.linlinjava.announce.goods.interfaces.rest.Callers;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceController;
import org.linlinjava.announce.order.api.OrderPaidV1;
import org.linlinjava.announce.order.api.OrderRefundedV1;
import org.linlinjava.announce.order.api.OrderTopics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole path with containers only (EventSourcingDB for the aggregate, MySQL loaded with the real schema and
 * seed for the read model): one announce of each kind is posted, published and read back from the projection
 * with its kind-specific fields; the kind invariants are checked over HTTP; the search projection feeds a recording
 * indexer instead of OCS. With Kafka: every publication is observed on {@code announce.v1}, and order.v1 events
 * injected by the test (the order module is not running) mark an announce SOLD and relist it.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // the suite runs as in production: only requests signed by an edge are served (the test profile says off)
        properties = {EdgeRequests.ENFORCE, EdgeRequests.SECRET_PROPERTY})
@ActiveProfiles("test")
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnnounceKindsEndToEndTest {

    static final String SQL = "../db-reactive/sql/";
    static final Duration WAIT = Duration.ofSeconds(60);
    static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    static final String SELLER = "7";
    static final int GOODS_CATEGORY = 1005001;   // seed: "Cars" under Vehicles
    static final int JOB_CATEGORY = 1037001;     // seed: "IT and software" under Jobs
    static final int EVENT_CATEGORY = 1038001;   // seed: "Concerts" under Events

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
            .withCopyFileToContainer(MountableFile.forHostPath(SQL + "announce_data_en.sql"), "/docker-entrypoint-initdb.d/04_seed.sql")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        CqrsContainers.register(registry, esdb, kafka, mysql);
        registry.add("spring.r2dbc.mybatis.r2dbc-url", () -> CqrsContainers.r2dbcUrl(mysql));
        registry.add("spring.r2dbc.mybatis.username", mysql::getUsername);
        registry.add("spring.r2dbc.mybatis.password", mysql::getPassword);
    }

    /** Probe of the service's own public topic, and a producer standing in for the order module. */
    @TestConfiguration
    static class KafkaProbeConfig {
        static final List<Object> announceEvents = new CopyOnWriteArrayList<>();

        @Bean
        Consumer<Message<byte[]>> announceProbe(PublicEventSubscribers subscribers) {
            return subscribers.forTopic("announce-probe")
                    .on(AnnouncePublishedV1.class, (event, headers) -> Mono.fromRunnable(() -> announceEvents.add(event)))
                    .on(AnnounceSoldV1.class, (event, headers) -> Mono.fromRunnable(() -> announceEvents.add(event)))
                    .build();
        }

        @Bean
        PublicEventTestProducer orderModuleStandIn(StreamBridge streamBridge, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
            return new PublicEventTestProducer(streamBridge, objectMapper, "order-test");
        }
    }

    @Autowired WebTestClient direct;   // straight to the service, as someone bypassing the edges would call it
    WebTestClient client;              // through the customer edge: every request carries a fresh X-Service-Token
    WebTestClient staff;               // through the admin edge, as staff member 1
    @Autowired RecordingSearchIndexer indexer;   // announce.search.mode=noop in the test profile
    @Autowired PublicEventTestProducer orderModule;

    static String goodsId;
    static String jobId;
    static String eventId;

    @BeforeEach
    void throughAnEdge() {
        client = direct.mutate().filter(EdgeRequests.signing()).build();
        staff = client.mutate().defaultHeader(Callers.USER_HEADER, "1").defaultHeader(Callers.TYPE_HEADER, Callers.ADMIN).build();
    }

    private WebTestClient.ResponseSpec post(String body) {
        return client.post().uri("/api/announces").header(Callers.USER_HEADER, SELLER).header(Callers.TYPE_HEADER, Callers.MEMBER)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private WebTestClient.ResponseSpec action(String id, String action, String seller) {
        return client.post().uri("/api/announces/" + id + "/" + action).header(Callers.USER_HEADER, seller).header(Callers.TYPE_HEADER, Callers.MEMBER).exchange();
    }

    private static String shared(String kind, String title, int category) {
        return "\"kind\":\"" + kind + "\",\"title\":\"" + title + "\",\"brief\":\"brief\",\"description\":\"<p>desc</p>\","
                + "\"keywords\":[\"e2e\"],\"gallery\":[\"http://x/1.jpg\"],\"categoryId\":" + category + ","
                + "\"location\":{\"regionId\":1,\"city\":\"Stockholm\",\"postalCode\":\"11122\"}";
    }

    private static String idOf(WebTestClient.ResponseSpec response) {
        byte[] body = response.expectStatus().isCreated().expectBody().jsonPath("$.status").isEqualTo("DRAFT").returnResult().getResponseBody();
        String json = new String(body);
        int i = json.indexOf("\"id\":\"") + 6;
        return json.substring(i, json.indexOf('"', i));
    }

    @Test
    @Order(0)
    void onlyRequestsThatPassedAnEdgeAreServed() {
        direct.get().uri("/api/announces").exchange().expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.type").isEqualTo(ServiceTokenWebFilter.PROBLEM_TYPE_MISSING);
        // identity headers alone are worth nothing, and neither is a token signed with a guessed secret or an old one
        direct.post().uri("/api/announces").header(Callers.USER_HEADER, SELLER).header(Callers.TYPE_HEADER, Callers.MEMBER)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isUnauthorized();
        direct.get().uri("/api/announces")
                .header(ServiceTokens.HEADER, ServiceTokens.mint("gateway-api", "guessed", Instant.now().getEpochSecond()))
                .exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.type").isEqualTo(ServiceTokenWebFilter.PROBLEM_TYPE_INVALID);
        direct.get().uri("/api/announces")
                .header(ServiceTokens.HEADER, ServiceTokens.mint("gateway-api", EdgeRequests.SECRET, Instant.now().getEpochSecond() - 600))
                .exchange().expectStatus().isUnauthorized();
        direct.get().uri("/out/dba-1").exchange().expectStatus().isUnauthorized();
        direct.post().uri("/api/admin/announces/reindex").header(Callers.USER_HEADER, "1").header(Callers.TYPE_HEADER, Callers.ADMIN)
                .exchange().expectStatus().isUnauthorized();
        // health checks reach the service directly
        direct.get().uri("/actuator/health").exchange().expectStatus().value(status -> assertThat(status).isNotEqualTo(401));

        client.get().uri("/api/announces").exchange().expectStatus().isOk();
        // a member coming through the customer edge is not staff
        client.get().uri("/api/admin/announces").header(Callers.USER_HEADER, SELLER).header(Callers.TYPE_HEADER, Callers.MEMBER)
                .exchange().expectStatus().isForbidden();
        staff.get().uri("/api/admin/announces?size=1").exchange().expectStatus().isOk();
    }

    @Test
    @Order(1)
    void seedLoadedIntoTheNewSchema() {
        client.get().uri("/api/announces?kind=GOODS&size=1").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(15))
                .jsonPath("$.items[0].kind").isEqualTo("GOODS").jsonPath("$.items[0].price").isNotEmpty();
        client.get().uri("/api/categories?kind=JOB").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].category.name").isEqualTo("Jobs").jsonPath("$[0].children.length()").isEqualTo(8);
    }

    @Test
    @Order(2)
    void oneAnnounceOfEachKindIsPostedPublishedAndReadBackFromTheProjection() {
        goodsId = idOf(post("{" + shared("GOODS", "E2E bike", GOODS_CATEGORY)
                + ",\"goods\":{\"price\":1500,\"condition\":\"GOOD\",\"saleType\":\"FIXED_PRICE\",\"unit\":\"pcs\","
                + "\"variants\":[{\"specifications\":[\"M\"],\"price\":1500,\"stock\":1}],\"attributes\":[{\"name\":\"Colour\",\"value\":\"Red\"}]}}"));
        jobId = idOf(post("{" + shared("JOB", "E2E Java developer", JOB_CATEGORY)
                + ",\"job\":{\"employer\":\"Acme\",\"contractType\":\"FULL_TIME\",\"salaryMin\":40000,\"salaryMax\":55000,\"currency\":\"SEK\","
                + "\"applicationDeadline\":\"" + NOW.plusDays(30) + "\",\"howToApply\":\"jobs@acme.invalid\"}}"));
        eventId = idOf(post("{" + shared("EVENT", "E2E flea market", EVENT_CATEGORY)
                + ",\"event\":{\"startsAt\":\"" + NOW.plusDays(3) + "\",\"endsAt\":\"" + NOW.plusDays(3).plusHours(6) + "\","
                + "\"venue\":\"Town square\",\"organizer\":\"The city\",\"ticketed\":true,\"ticketPrice\":5,\"capacity\":200}}"));

        action(goodsId, "publish", SELLER).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("PUBLISHED").jsonPath("$.expiresAt").isNotEmpty();
        action(jobId, "publish", SELLER).expectStatus().isOk().expectBody().jsonPath("$.kind").isEqualTo("JOB");
        action(eventId, "publish", SELLER).expectStatus().isOk().expectBody().jsonPath("$.kind").isEqualTo("EVENT");

        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/announces/" + goodsId).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.kind").isEqualTo("GOODS").jsonPath("$.status").isEqualTo("PUBLISHED")
                .jsonPath("$.goods.price").isEqualTo(1500.0).jsonPath("$.goods.condition").isEqualTo("GOOD")
                .jsonPath("$.goods.variants[0].specifications[0]").isEqualTo("M").jsonPath("$.goods.attributes[0].value").isEqualTo("Red")
                .jsonPath("$.location.city").isEqualTo("Stockholm").jsonPath("$.description").isEqualTo("<p>desc</p>"));
        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/announces/" + jobId).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.kind").isEqualTo("JOB").jsonPath("$.status").isEqualTo("PUBLISHED")
                .jsonPath("$.job.employer").isEqualTo("Acme").jsonPath("$.job.currency").isEqualTo("SEK").jsonPath("$.job.salaryMax").isEqualTo(55000.0)
                .jsonPath("$.job.howToApply").isEqualTo("jobs@acme.invalid").jsonPath("$.goods").doesNotExist());
        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/announces/" + eventId).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.kind").isEqualTo("EVENT").jsonPath("$.status").isEqualTo("PUBLISHED")
                .jsonPath("$.event.venue").isEqualTo("Town square").jsonPath("$.event.ticketed").isEqualTo(true)
                .jsonPath("$.event.ticketPrice").isEqualTo(5.0).jsonPath("$.event.capacity").isEqualTo(200));

        // listing per kind reads the joined read model
        client.get().uri("/api/announces?kind=JOB&q=E2E").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.total").isEqualTo(1).jsonPath("$.items[0].id").isEqualTo(jobId)
                .jsonPath("$.items[0].employer").isEqualTo("Acme").jsonPath("$.items[0].contractType").isEqualTo("FULL_TIME");
        client.get().uri("/api/announces?kind=EVENT&q=E2E").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.items[0].id").isEqualTo(eventId).jsonPath("$.items[0].ticketed").isEqualTo(true);
        client.get().uri("/api/announces?q=E2E&sort=price_desc").exchange().expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(3);

        // the search projection indexed all three with kind as a facet
        await().atMost(WAIT).untilAsserted(() -> assertThat(indexer.indexed()).extracting(RecordingSearchIndexer.Indexed::id)
                .contains(goodsId, jobId, eventId));
        Map<String, Object> jobDoc = indexer.indexed().stream().filter(d -> d.id().equals(jobId)).findFirst().orElseThrow().data();
        assertThat(jobDoc).containsEntry("kind", "JOB").containsEntry("employer", "Acme").containsKey("application_deadline_epoch").doesNotContainKey("price");
        Map<String, Object> goodsDoc = indexer.indexed().stream().filter(d -> d.id().equals(goodsId)).findFirst().orElseThrow().data();
        assertThat(goodsDoc).containsEntry("kind", "GOODS").containsEntry("Colour", "Red").containsEntry("city", "Stockholm");
    }

    @Test
    @Order(3)
    void reindexPushesEveryPublishedAnnounceFromTheReadModel() {
        // the admin trigger walks the read model, so rows that never produced an event (the seed) reach the index too
        int before = indexer.indexed().size();
        staff.mutate().responseTimeout(Duration.ofMinutes(3)).build()
                .post().uri("/api/admin/announces/reindex").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.indexed").value(n -> assertThat(((Number) n).longValue()).isGreaterThanOrEqualTo(3L));
        assertThat(indexer.indexed().size()).isGreaterThan(before);
        assertThat(indexer.indexed().subList(before, indexer.indexed().size())).extracting(RecordingSearchIndexer.Indexed::id)
                .contains(goodsId, jobId, eventId);
        Map<String, Object> seed = indexer.indexed().subList(before, indexer.indexed().size()).stream()
                .filter(d -> d.id().equals("2000001")).findFirst().orElseThrow().data();
        assertThat(seed.get("source")).isEqualTo("local");
        assertThat(String.valueOf(seed.get("image_url"))).startsWith("http");
    }

    @Test
    @Order(4)
    void kindInvariantsAreEnforcedOverHttp() {
        // an event that ends before it starts is accepted as a draft but cannot be published
        String backwards = idOf(post("{" + shared("EVENT", "E2E backwards", EVENT_CATEGORY)
                + ",\"event\":{\"startsAt\":\"" + NOW.plusDays(3) + "\",\"endsAt\":\"" + NOW.plusDays(2) + "\"}}"));
        action(backwards, "publish", SELLER).expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("event-dates");

        // a job with an auction sale type is rejected outright
        post("{" + shared("JOB", "E2E auctioned job", JOB_CATEGORY)
                + ",\"job\":{\"employer\":\"Acme\",\"howToApply\":\"jobs@acme.invalid\",\"saleType\":\"AUCTION\"}}")
                .expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("not-biddable");

        // sold applies to goods only; a job with a goods category is a bad request; only the owner writes
        action(jobId, "sold", SELLER).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("sold-not-applicable");
        post("{" + shared("JOB", "E2E wrong tree", GOODS_CATEGORY) + ",\"job\":{\"employer\":\"Acme\",\"howToApply\":\"x@y.invalid\"}}")
                .expectStatus().isBadRequest();
        action(goodsId, "withdraw", "8").expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("not-owner");
        client.get().uri("/api/announces/" + goodsId + "/nothing").exchange().expectStatus().isNotFound();
    }

    @Test
    @Order(5)
    void withdrawnAndSoldAnnouncesLeaveTheListingAndTheIndex() {
        action(goodsId, "sold", SELLER).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("SOLD");
        action(eventId, "withdraw", SELLER).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("WITHDRAWN");
        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/announces/" + goodsId).exchange().expectStatus().isNotFound());
        client.get().uri("/api/announces/" + goodsId).header(Callers.USER_HEADER, SELLER).header(Callers.TYPE_HEADER, Callers.MEMBER).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("SOLD");
        await().atMost(WAIT).untilAsserted(() -> assertThat(indexer.removed()).contains(goodsId, eventId));
        client.get().uri("/api/announces?q=E2E").exchange().expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(1);

        // idempotent retry of a command id replays the stored result
        client.put().uri("/api/announces/" + jobId).header(Callers.USER_HEADER, SELLER).header(Callers.TYPE_HEADER, Callers.MEMBER).header(AnnounceController.COMMAND_ID_HEADER, "edit-job-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{" + shared("JOB", "E2E Java developer (senior)", JOB_CATEGORY) + ",\"job\":{\"employer\":\"Acme\",\"howToApply\":\"jobs@acme.invalid\"}}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.title").isEqualTo("E2E Java developer (senior)");
        client.put().uri("/api/announces/" + jobId).header(Callers.USER_HEADER, SELLER).header(Callers.TYPE_HEADER, Callers.MEMBER).header(AnnounceController.COMMAND_ID_HEADER, "edit-job-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{" + shared("JOB", "ignored on replay", JOB_CATEGORY) + ",\"job\":{\"employer\":\"Acme\",\"howToApply\":\"jobs@acme.invalid\"}}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.title").isEqualTo("E2E Java developer (senior)");
        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/announces/" + jobId).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.title").isEqualTo("E2E Java developer (senior)").jsonPath("$.job.salaryMax").doesNotExist());
    }

    @Test
    @Order(6)
    void publicEventsGoOutOnAnnounceV1AndOrderV1EventsSellAndRelistTheAnnounce() {
        // every publication of test 2 went out with the snapshot other contexts need
        await().atMost(WAIT).untilAsserted(() -> assertThat(published(goodsId)).isNotEmpty());
        AnnouncePublishedV1 bike = published(goodsId).get(0);
        assertThat(bike.kind()).isEqualTo("GOODS");
        assertThat(bike.sellerId()).isEqualTo(Integer.valueOf(SELLER));
        assertThat(bike.title()).isEqualTo("E2E bike");
        assertThat(bike.mainPicture()).isEqualTo("http://x/1.jpg");
        assertThat(bike.priceCents()).isEqualTo(150000L);
        assertThat(bike.currency()).isEqualTo("DKK");
        assertThat(bike.saleType()).isEqualTo("FIXED_PRICE");
        assertThat(bike.categoryId()).isEqualTo(GOODS_CATEGORY);
        assertThat(bike.expiresAt()).isAfter(NOW.plusDays(59));
        assertThat(published(jobId).get(0).saleType()).isNull();
        assertThat(published(jobId).get(0).priceCents()).isZero();
        // and the manual sale of test 4 was relayed
        await().atMost(WAIT).untilAsserted(() -> assertThat(KafkaProbeConfig.announceEvents)
                .filteredOn(e -> e instanceof AnnounceSoldV1 sold && sold.announceId().equals(goodsId)).hasSize(1));

        // a second goods announce is sold by the order module ...
        String sofaId = idOf(post("{" + shared("GOODS", "E2E sofa", GOODS_CATEGORY) + ",\"goods\":{\"price\":800,\"condition\":\"GOOD\"}}"));
        action(sofaId, "publish", SELLER).expectStatus().isOk();
        await().atMost(WAIT).untilAsserted(() -> assertThat(published(sofaId)).hasSize(1));
        String paidEventId = orderModule.send(OrderTopics.ORDER_V1, "/order/o-1",
                new OrderPaidV1("o-1", sofaId, 9, Integer.valueOf(SELLER), 80000L, "DKK", NOW));
        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/announces/" + sofaId).header(Callers.USER_HEADER, SELLER).header(Callers.TYPE_HEADER, Callers.MEMBER)
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("SOLD"));
        await().atMost(WAIT).untilAsserted(() -> assertThat(KafkaProbeConfig.announceEvents)
                .filteredOn(e -> e instanceof AnnounceSoldV1 sold && sold.announceId().equals(sofaId)).hasSize(1));
        // ... a redelivered paid record is a no-op (command id replay), a paid record for an unknown announce is skipped ...
        orderModule.send(OrderTopics.ORDER_V1, "/order/o-1", new OrderPaidV1("o-1", sofaId, 9, Integer.valueOf(SELLER), 80000L, "DKK", NOW), paidEventId);
        orderModule.send(OrderTopics.ORDER_V1, "/order/o-2", new OrderPaidV1("o-2", "no-such-announce", 9, 1, 100L, "DKK", NOW));
        // ... and the refund relists it: PUBLISHED again with the same expiry, published once more on announce.v1
        await().atMost(WAIT).untilAsserted(() -> assertThat(indexer.removed()).contains(sofaId));   // the sale left the index
        indexer.clear();
        orderModule.send(OrderTopics.ORDER_V1, "/order/o-1",
                new OrderRefundedV1("o-1", sofaId, 9, Integer.valueOf(SELLER), 80000L, "DKK", "ship window expired", NOW));
        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/announces/" + sofaId).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PUBLISHED"));
        await().atMost(WAIT).untilAsserted(() -> assertThat(published(sofaId)).hasSize(2));
        assertThat(published(sofaId).get(1).expiresAt()).isEqualTo(published(sofaId).get(0).expiresAt());
        assertThat(published(sofaId).get(1).publishedAt()).isAfter(published(sofaId).get(0).publishedAt());
        assertThat(KafkaProbeConfig.announceEvents).filteredOn(e -> e instanceof AnnounceSoldV1 sold && sold.announceId().equals(sofaId)).hasSize(1);
        // the search projection is at-least-once and derives from the read model, so it may skip an intermediate state
        // (it can catch up on the publish when the row is already SOLD); what counts is that it ends with the announce indexed
        await().atMost(WAIT).untilAsserted(() -> assertThat(indexer.indexCount(sofaId)).isGreaterThanOrEqualTo(1));   // back in the index
    }

    private static List<AnnouncePublishedV1> published(String announceId) {
        return KafkaProbeConfig.announceEvents.stream()
                .filter(e -> e instanceof AnnouncePublishedV1 p && p.announceId().equals(announceId))
                .map(AnnouncePublishedV1.class::cast).toList();
    }

    @Test
    @Order(5)
        void staffModeratesAnnouncesThroughTheAdminControllerAndTheReasonReachesTheReadModel() {
        // a fresh published goods announce of another seller; staff withdraws it with a reason, no owner header
        String spam = idOf(post("{" + shared("GOODS", "E2E spam bike", GOODS_CATEGORY) + ",\"goods\":{\"price\":10,\"condition\":\"FAIR\",\"saleType\":\"FIXED_PRICE\"}}"));
        action(spam, "publish", SELLER).expectStatus().isOk();
        staff.post().uri("/api/admin/announces/" + spam + "/withdraw")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"reason\":\"spam\"}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("WITHDRAWN");
        // the still-published job is force-expired although its date is far away
        staff.post().uri("/api/admin/announces/" + jobId + "/expire")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"reason\":\"employer asked\"}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("EXPIRED");
        // a reason is mandatory; a draft cannot be moderated
        staff.post().uri("/api/admin/announces/" + jobId + "/withdraw")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"reason\":\"\"}").exchange().expectStatus().isBadRequest();
        staff.post().uri("/api/admin/announces/" + jobId + "/withdraw")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"reason\":\"again\"}").exchange()
                .expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("not-published");

        // the admin listing shows every status with the moderation columns; the public listing hides them; the index drops them
        await().atMost(WAIT).untilAsserted(() -> staff.get().uri("/api/admin/announces?status=WITHDRAWN&q=E2E spam").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.total").isEqualTo(1).jsonPath("$.items[0].id").isEqualTo(spam)
                .jsonPath("$.items[0].moderatedBy").isEqualTo(1).jsonPath("$.items[0].moderationReason").isEqualTo("spam"));
        await().atMost(WAIT).untilAsserted(() -> staff.get().uri("/api/admin/announces?kind=JOB&status=EXPIRED&q=E2E").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.items[0].id").isEqualTo(jobId).jsonPath("$.items[0].moderationReason").isEqualTo("employer asked"));
        staff.get().uri("/api/admin/announces/" + spam).exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("WITHDRAWN");
        client.get().uri("/api/announces/" + spam).exchange().expectStatus().isNotFound();
        client.get().uri("/api/announces?q=E2E").exchange().expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(0);
        await().atMost(WAIT).untilAsserted(() -> assertThat(indexer.removed()).contains(spam, jobId));
        // the seller republishes the moderated job and the moderation columns clear
        action(jobId, "publish", SELLER).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("PUBLISHED");
        await().atMost(WAIT).untilAsserted(() -> staff.get().uri("/api/admin/announces?kind=JOB&status=PUBLISHED&q=E2E").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.items[0].id").isEqualTo(jobId).jsonPath("$.items[0].moderationReason").doesNotExist());
    }
}
