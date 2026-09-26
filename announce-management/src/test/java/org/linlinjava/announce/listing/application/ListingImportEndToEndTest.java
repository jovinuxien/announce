package org.linlinjava.announce.listing.application;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.linlinjava.announce.cqrs.test.containers.CqrsContainers;
import org.linlinjava.announce.cqrs.test.containers.EventSourcingDbContainer;
import org.linlinjava.announce.goods.AnnounceGoodsManagement;
import org.linlinjava.announce.listing.domain.CrawledListing;
import org.linlinjava.announce.listing.domain.CrawledListingRepository;
import org.linlinjava.announce.listing.domain.ListingSource;
import org.linlinjava.announce.listing.domain.ListingStatus;
import org.linlinjava.announce.listing.infrastructure.integration.ListingImportFlow;
import org.linlinjava.announce.search.RecordingSearchIndexer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The crawled-listing path with containers only (MySQL loaded with the real schema; EventSourcingDB because the
 * service also hosts the announce aggregate): the fixtures (three dba items, two tradera items) are imported twice,
 * a changed price is re-indexed once, a stale row is swept GONE and removed from the index, {@code /out/{id}}
 * redirects or answers 410, the admin endpoints import and reindex, the Spring Integration flow polls a directory
 * and moves each file to {@code done/} or {@code failed/}, and the real {@code dba_cars.json} loads when present.
 */
@Testcontainers
@SpringBootTest(classes = AnnounceGoodsManagement.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ListingImportEndToEndTest {

    static final String SQL = "../db-reactive/sql/";
    static final Path FIXTURES = Path.of("src/test/resources/listings");
    static final Path REAL_DBA_FEED = Path.of(System.getProperty("user.home"), "announce_crawled_data", "dba_cars.json");
    static final Duration WAIT = Duration.ofSeconds(30);
    static final String AYGO = "dba-1117479380";
    static final String FIAT = "dba-1117478095";
    static final String VOLVO = "tradera-640012345";
    static final Path IMPORT_DIR = tempDir();

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
        registry.add("announce.listing.import.enabled", () -> "true");
        registry.add("announce.listing.import.dir", IMPORT_DIR::toString);
        registry.add("announce.listing.import.poll", () -> "PT1S");
    }

    static Path tempDir() {
        try {
            return Files.createTempDirectory("announce-listings-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired WebTestClient client;
    @Autowired ListingImportService importer;
    @Autowired ListingSweepService sweep;
    @Autowired CrawledListingRepository listings;
    @Autowired RecordingSearchIndexer indexer;

    private ImportReport importAll(String dbaFile) {
        ImportReport dba = importer.importFile(ListingSource.DBA, FIXTURES.resolve(dbaFile)).block();
        ImportReport tradera = importer.importFile(ListingSource.TRADERA, FIXTURES.resolve("tradera_fixture.jsonl")).block();
        return dba.plus(tradera);
    }

    @Test
    @Order(1)
    void fiveItemsImportedTwiceGiveFiveRowsAndFiveIndexCallsThenNone() {
        indexer.clear();
        ImportReport first = importAll("dba_fixture.json");
        assertThat(first).isEqualTo(new ImportReport(5, 5, 0, 0, 0));
        assertThat(indexer.indexed()).hasSize(5);
        Map<String, Object> aygo = indexer.indexed().stream().filter(d -> d.id().equals(AYGO)).findFirst().orElseThrow().data();
        assertThat(aygo).containsEntry("source", "dba").containsEntry("kind", "GOODS").containsEntry("city", "Vamdrup")
                .containsEntry("postal_code", "6580").containsEntry("km", "273000").containsEntry("model_year", "2007")
                .containsEntry("source_category", "Cars/Private Car").containsEntry("external_url", "https://www.dba.dk/toyota-aygo/id-1117479380/")
                .doesNotContainKey("category_ids").doesNotContainKey("region_id");
        Map<String, Object> volvo = indexer.indexed().stream().filter(d -> d.id().equals(VOLVO)).findFirst().orElseThrow().data();
        assertThat(volvo).containsEntry("source", "tradera").containsEntry("sale_type", "AUCTION").containsKey("ends_epoch");

        ImportReport second = importAll("dba_fixture.json");
        assertThat(second).isEqualTo(new ImportReport(5, 0, 0, 5, 0));
        assertThat(indexer.indexed()).as("an unchanged run indexes nothing").hasSize(5);

        CrawledListing row = listings.findById(FIAT).block();
        assertThat(row.attributes()).containsEntry("dealer", "Brdr. Møll A/S");
        assertThat(row.address().city()).isEqualTo("Hjørring");
        assertThat(row.status()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    @Order(2)
    void aChangedPriceYieldsOneReindex() {
        indexer.clear();
        ImportReport report = importer.importFile(ListingSource.DBA, FIXTURES.resolve("dba_fixture_price_changed.json")).block();
        assertThat(report).isEqualTo(new ImportReport(3, 0, 1, 2, 0));
        assertThat(indexer.indexed()).hasSize(1);
        assertThat(indexer.indexed().get(0).id()).isEqualTo(AYGO);
        assertThat((BigDecimal) indexer.indexed().get(0).data().get("price")).isEqualByComparingTo("21900");
        assertThat(listings.findById(AYGO).block().price()).isEqualByComparingTo("21900");
    }

    @Test
    @Order(3)
    void aRowNotSeenForTheGracePeriodIsGoneAndRemovedFromTheIndex() {
        indexer.clear();
        LocalDateTime tooOld = LocalDateTime.now().minus(sweep.goneAfter()).minusDays(1);
        listings.touchSeen(FIAT, tooOld).block();
        assertThat(sweep.sweep(100).block()).isEqualTo(1L);
        assertThat(listings.findById(FIAT).block().status()).isEqualTo(ListingStatus.GONE);
        assertThat(indexer.removed()).containsExactly(FIAT);
        assertThat(sweep.sweep(100).block()).as("a second pass finds nothing").isZero();
    }

    @Test
    @Order(4)
    void clickOutRedirectsActiveAnswers410ForGoneAnd404ForUnknown() {
        client.get().uri("/out/" + AYGO).exchange().expectStatus().isFound()
                .expectHeader().location("https://www.dba.dk/toyota-aygo/id-1117479380/");
        client.get().uri("/out/" + AYGO).exchange().expectStatus().isFound();
        assertThat(listings.findById(AYGO).block().clickCount()).isEqualTo(2);
        client.get().uri("/out/" + FIAT).exchange().expectStatus().isEqualTo(410);
        client.get().uri("/out/dba-0").exchange().expectStatus().isNotFound();
    }

    @Test
    @Order(5)
    void aGoneListingSeenAgainIsActiveAndIndexedAgain() {
        indexer.clear();
        ImportReport report = importer.importFile(ListingSource.DBA, FIXTURES.resolve("dba_fixture_price_changed.json")).block();
        assertThat(report).isEqualTo(new ImportReport(3, 0, 1, 2, 0));
        assertThat(indexer.indexed()).extracting(RecordingSearchIndexer.Indexed::id).containsExactly(FIAT);
        CrawledListing fiat = listings.findById(FIAT).block();
        assertThat(fiat.status()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(fiat.clickCount()).isZero();
        client.get().uri("/out/" + FIAT).exchange().expectStatus().isFound();
    }

    @Test
    @Order(6)
    void adminEndpointsImportAFileAndReindexEveryActiveRow() {
        indexer.clear();
        client.post().uri("/api/admin/listings/import").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"path\":\"" + FIXTURES.resolve("tradera_fixture.jsonl").toAbsolutePath() + "\"}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.seen").isEqualTo(2).jsonPath("$.unchanged").isEqualTo(2);
        client.post().uri("/api/admin/listings/import").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"path\":\"/nowhere/dba_x.json\"}").exchange().expectStatus().isBadRequest();
        client.post().uri("/api/admin/listings/reindex").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.indexed").isEqualTo(5);
        assertThat(indexer.indexed()).hasSize(5);
    }

    @Test
    @Order(7)
    void thePollingFlowImportsAFileDroppedInTheDirectoryAndMovesIt() throws IOException {
        Files.copy(FIXTURES.resolve("dba_flow_fixture.json"), IMPORT_DIR.resolve("dba_202410080001.json"));
        Files.copy(FIXTURES.resolve("tradera_broken.jsonl"), IMPORT_DIR.resolve("tradera_202410080002.jsonl"));
        Files.writeString(IMPORT_DIR.resolve("notes.txt"), "ignored");
        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(IMPORT_DIR.resolve(ListingImportFlow.DONE_DIR).resolve("dba_202410080001.json")).exists();
            assertThat(IMPORT_DIR.resolve(ListingImportFlow.FAILED_DIR).resolve("tradera_202410080002.jsonl")).exists();
        });
        assertThat(IMPORT_DIR.resolve("dba_202410080001.json")).doesNotExist();
        assertThat(IMPORT_DIR.resolve("notes.txt")).exists();
        assertThat(listings.findById("dba-1117400001").block().title()).startsWith("Ford Ka");
        assertThat(listings.findById("tradera-1").block()).as("the good line before the broken one was imported").isNotNull();
    }

    @Test
    @Order(8)
    void theRealDbaFeedLoadsWithoutErrors() {
        Assumptions.assumeTrue(Files.isRegularFile(REAL_DBA_FEED), "no " + REAL_DBA_FEED);
        ImportReport report = importer.importFile(ListingSource.DBA, REAL_DBA_FEED).block();
        assertThat(report.rejected()).isZero();
        assertThat(report.seen()).isEqualTo(54);
        assertThat(report.inserted() + report.updated() + report.unchanged()).isEqualTo(54);
    }
}
