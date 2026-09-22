package org.linlinjava.announce.goods.application.announce;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.linlinjava.announce.cqrs.test.containers.CqrsContainers;
import org.linlinjava.announce.cqrs.test.containers.EventSourcingDbContainer;
import org.linlinjava.announce.goods.application.search.AnnounceDocument;
import org.linlinjava.announce.goods.application.search.AnnounceIndexer;
import org.linlinjava.announce.goods.interfaces.rest.announce.AnnounceController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole path with containers only (EventSourcingDB for the aggregate, MySQL loaded with the real schema and
 * seed for the read model): one announce of each kind is posted, published and read back from the projection
 * with its kind-specific fields; the kind invariants are checked over HTTP; the search projection feeds a recording
 * indexer instead of OCS.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnnounceKindsEndToEndTest {

    static final String SQL = "../db-reactive/sql/";
    static final Duration WAIT = Duration.ofSeconds(60);
    static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    static final String SELLER = "7";
    static final int GOODS_CATEGORY = 1005007;   // seed: a GOODS child category
    static final int JOB_CATEGORY = 1037001;     // seed: "IT and software" under Jobs
    static final int EVENT_CATEGORY = 1038001;   // seed: "Concerts" under Events

    @Container
    static final EventSourcingDbContainer esdb = CqrsContainers.eventSourcingDb();

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>(CqrsContainers.MYSQL_IMAGE)
            .withDatabaseName("announce_reactive_db").withUsername("announce").withPassword("announce")
            .withCopyFileToContainer(MountableFile.forHostPath(SQL + "announce_table.sql"), "/docker-entrypoint-initdb.d/01_tables.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(SQL + "announce_auction_chat.sql"), "/docker-entrypoint-initdb.d/02_auction_chat.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(SQL + "announce_data_en.sql"), "/docker-entrypoint-initdb.d/03_seed.sql")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        CqrsContainers.register(registry, esdb, null, mysql);
        registry.add("spring.r2dbc.mybatis.r2dbc-url", () -> CqrsContainers.r2dbcUrl(mysql));
        registry.add("spring.r2dbc.mybatis.username", mysql::getUsername);
        registry.add("spring.r2dbc.mybatis.password", mysql::getPassword);
    }

    /** Stands in for the OCS indexer: remembers every document put and every id removed. */
    @TestConfiguration
    static class RecordingIndexerConfig {
        static final List<AnnounceDocument> indexed = new CopyOnWriteArrayList<>();
        static final List<String> removed = new CopyOnWriteArrayList<>();

        @Bean
        AnnounceIndexer recordingIndexer() {
            return new AnnounceIndexer() {
                @Override public Mono<Void> index(AnnounceDocument document) { return Mono.fromRunnable(() -> indexed.add(document)); }
                @Override public Mono<Void> remove(String announceId) { return Mono.fromRunnable(() -> removed.add(announceId)); }
            };
        }
    }

    @Autowired WebTestClient client;

    static String goodsId;
    static String jobId;
    static String eventId;

    private WebTestClient.ResponseSpec post(String body) {
        return client.post().uri("/api/announces").header(AnnounceController.SELLER_HEADER, SELLER)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private WebTestClient.ResponseSpec action(String id, String action, String seller) {
        return client.post().uri("/api/announces/" + id + "/" + action).header(AnnounceController.SELLER_HEADER, seller).exchange();
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
    @Order(1)
    void seedLoadedIntoTheNewSchema() {
        client.get().uri("/api/announces?kind=GOODS&size=1").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(238))
                .jsonPath("$.items[0].kind").isEqualTo("GOODS").jsonPath("$.items[0].price").isNotEmpty();
        client.get().uri("/api/categories?kind=JOB").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].category.name").isEqualTo("Jobs").jsonPath("$[0].children.length()").isEqualTo(3);
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
        await().atMost(WAIT).untilAsserted(() -> assertThat(RecordingIndexerConfig.indexed).extracting(AnnounceDocument::id)
                .contains(goodsId, jobId, eventId));
        Map<String, Object> jobDoc = RecordingIndexerConfig.indexed.stream().filter(d -> d.id().equals(jobId)).findFirst().orElseThrow().data();
        assertThat(jobDoc).containsEntry("kind", "JOB").containsEntry("employer", "Acme").containsKey("application_deadline_epoch").doesNotContainKey("price");
        Map<String, Object> goodsDoc = RecordingIndexerConfig.indexed.stream().filter(d -> d.id().equals(goodsId)).findFirst().orElseThrow().data();
        assertThat(goodsDoc).containsEntry("kind", "GOODS").containsEntry("Colour", "Red").containsEntry("city", "Stockholm");
    }

    @Test
    @Order(3)
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
    @Order(4)
    void withdrawnAndSoldAnnouncesLeaveTheListingAndTheIndex() {
        action(goodsId, "sold", SELLER).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("SOLD");
        action(eventId, "withdraw", SELLER).expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("WITHDRAWN");
        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/announces/" + goodsId).exchange().expectStatus().isNotFound());
        client.get().uri("/api/announces/" + goodsId).header(AnnounceController.SELLER_HEADER, SELLER).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("SOLD");
        await().atMost(WAIT).untilAsserted(() -> assertThat(RecordingIndexerConfig.removed).contains(goodsId, eventId));
        client.get().uri("/api/announces?q=E2E").exchange().expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(1);

        // idempotent retry of a command id replays the stored result
        client.put().uri("/api/announces/" + jobId).header(AnnounceController.SELLER_HEADER, SELLER).header(AnnounceController.COMMAND_ID_HEADER, "edit-job-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{" + shared("JOB", "E2E Java developer (senior)", JOB_CATEGORY) + ",\"job\":{\"employer\":\"Acme\",\"howToApply\":\"jobs@acme.invalid\"}}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.title").isEqualTo("E2E Java developer (senior)");
        client.put().uri("/api/announces/" + jobId).header(AnnounceController.SELLER_HEADER, SELLER).header(AnnounceController.COMMAND_ID_HEADER, "edit-job-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{" + shared("JOB", "ignored on replay", JOB_CATEGORY) + ",\"job\":{\"employer\":\"Acme\",\"howToApply\":\"jobs@acme.invalid\"}}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.title").isEqualTo("E2E Java developer (senior)");
        await().atMost(WAIT).untilAsserted(() -> client.get().uri("/api/announces/" + jobId).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.title").isEqualTo("E2E Java developer (senior)").jsonPath("$.job.salaryMax").doesNotExist());
    }
}
