package org.linlinjava.announce.cqrs.sample;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.linlinjava.announce.cqrs.readside.ProjectionRebuilder;
import org.linlinjava.announce.cqrs.sample.ListingPublicEvents.ListingCreatedV1;
import org.linlinjava.announce.cqrs.sample.ListingPublicEvents.ListingPriceChangedV1;
import org.linlinjava.announce.cqrs.test.containers.CqrsContainers;
import org.linlinjava.announce.cqrs.test.containers.EventSourcingDbContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * HTTP command -> EventSourcingDB -> projection in MySQL (checkpointed) -> public topic on Kafka -> subscriber,
 * plus idempotent retries, problem details and a rebuild of the read model.
 */
// @Testcontainers is registered before @SpringBootTest so that, after the class, Spring closes the context
// (@DirtiesContext) before Testcontainers stops the containers: the event processors are stopped while the
// database is still there instead of failing against a vanished container.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SampleEndToEndTest {

    @Container
    static final EventSourcingDbContainer esdb = CqrsContainers.eventSourcingDb();
    @Container
    static final KafkaContainer kafka = CqrsContainers.kafka();
    @Container
    static final MySQLContainer<?> mysql = CqrsContainers.mysql();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        CqrsContainers.register(registry, esdb, kafka, mysql);
    }

    @Autowired
    WebTestClient client;
    @Autowired
    ListingSummaryProjection summaries;
    @Autowired
    ListingMessaging.ReceivedPublicEvents received;
    @Autowired
    ProjectionRebuilder rebuilder;
    @Autowired
    io.r2dbc.spi.ConnectionFactory connectionFactory;

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Test
    @Order(1)
    void commandIsHandledProjectedAndPublished() {
        client.post().uri("/listings").contentType(MediaType.APPLICATION_JSON)
                .header("X-Command-Id", "create-1").header("X-Correlation-Id", "corr-e2e")
                .bodyValue(new ListingController.CreateRequest("1", "Bike", 12000))
                .exchange().expectStatus().isCreated()
                .expectHeader().valueEquals("X-Correlation-Id", "corr-e2e")
                .expectBody().jsonPath("$.id").isEqualTo("1");

        await().atMost(WAIT).untilAsserted(() -> {
            ListingSummaryProjection.Summary summary = client.get().uri("/listings/1").exchange().expectStatus().isOk()
                    .expectBody(ListingSummaryProjection.Summary.class).returnResult().getResponseBody();
            assertThat(summary).isNotNull();
            assertThat(summary.title()).isEqualTo("Bike");
            assertThat(summary.priceCents()).isEqualTo(12000);
            assertThat(summary.status()).isEqualTo("OPEN");
        });

        await().atMost(WAIT).untilAsserted(() -> assertThat(received.all()).hasSize(1));
        ListingMessaging.Received created = received.all().get(0);
        assertThat(created.event()).isEqualTo(new ListingCreatedV1("1", "Bike", 12000));
        assertThat(created.headers().eventType()).isEqualTo("listing.created");
        assertThat(created.headers().eventVersion()).isEqualTo(1);
        assertThat(created.headers().subject()).contains("/listing/1");
        assertThat(created.headers().correlationId()).contains("corr-e2e");
        assertThat(created.headers().eventId()).isPresent();

        Map<String, Object> progress = summaries.progress().block(WAIT);
        assertThat(progress).isNotNull().containsEntry("group", ListingSummaryProjection.GROUP);
        assertThat(progress.get("eventId")).isNotNull();
    }

    @Test
    @Order(2)
    void retriedCommandIdReplaysTheResultWithoutRunningTheHandlerAgain() {
        client.post().uri("/listings").contentType(MediaType.APPLICATION_JSON)
                .header("X-Command-Id", "create-1")
                .bodyValue(new ListingController.CreateRequest("1", "Bike", 12000))
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.id").isEqualTo("1");

        Long completed = DatabaseClient.create(connectionFactory)
                .sql("SELECT completed FROM cqrs_command_log WHERE command_id = 'create-1'")
                .map((row, meta) -> row.get("completed", Long.class)).one().block(WAIT);
        assertThat(completed).isEqualTo(1L);

        // the same command without an id is a real second dispatch: the subject is no longer pristine
        client.post().uri("/listings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ListingController.CreateRequest("1", "Bike", 12000))
                .exchange().expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.type").isEqualTo("urn:announce:cqrs:subject-already-exists")
                .jsonPath("$.subject").isEqualTo("/listing/1");
    }

    @Test
    @Order(3)
    void businessRulesAndMissingSubjectsBecomeProblemDetails() {
        client.put().uri("/listings/1/price").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ListingController.PriceRequest(-5))
                .exchange().expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.type").isEqualTo("urn:announce:cqrs:command-rejected")
                .jsonPath("$.code").isEqualTo("price-not-positive");

        client.put().uri("/listings/404/price").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ListingController.PriceRequest(100))
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.type").isEqualTo("urn:announce:cqrs:subject-not-found");
    }

    @Test
    @Order(4)
    void priceChangeFlowsToReadModelAndTopicInOrder() {
        client.put().uri("/listings/1/price").contentType(MediaType.APPLICATION_JSON)
                .header("X-Command-Id", "price-1")
                .bodyValue(new ListingController.PriceRequest(9900))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.priceCents").isEqualTo(9900);

        await().atMost(WAIT).untilAsserted(() -> assertThat(summaries.find("1").block(WAIT)).get()
                .extracting(ListingSummaryProjection.Summary::priceCents).isEqualTo(9900L));
        await().atMost(WAIT).untilAsserted(() -> assertThat(received.all()).hasSize(2));
        assertThat(received.all().get(1).event()).isEqualTo(new ListingPriceChangedV1("1", 9900));
        assertThat(received.all().get(1).headers().causationId()).isEqualTo(received.all().get(1).headers().eventId());
    }

    @Test
    @Order(5)
    void rebuildReplaysTheLogIntoAnEmptyReadModel() {
        String lastEventBefore = summaries.find("1").block(WAIT).orElseThrow().lastEventId();

        rebuilder.rebuild(ListingSummaryProjection.GROUP).block(WAIT);

        await().atMost(WAIT).untilAsserted(() -> {
            ListingSummaryProjection.Summary summary = summaries.find("1").block(WAIT).orElse(null);
            assertThat(summary).isNotNull();
            assertThat(summary.priceCents()).isEqualTo(9900L);
            assertThat(summary.lastEventId()).isEqualTo(lastEventBefore);
        });
        assertThat(summaries.progress().block(WAIT)).isNotNull().containsEntry("eventId", lastEventBefore);
    }

    @Test
    @Order(6)
    void closingTwiceIsRejected() {
        client.post().uri("/listings/1/close").exchange().expectStatus().isNoContent();
        client.post().uri("/listings/1/close").exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("listing-closed");
        await().atMost(WAIT).untilAsserted(() -> assertThat(summaries.find("1").block(WAIT)).get()
                .extracting(ListingSummaryProjection.Summary::status).isEqualTo("CLOSED"));
    }
}
