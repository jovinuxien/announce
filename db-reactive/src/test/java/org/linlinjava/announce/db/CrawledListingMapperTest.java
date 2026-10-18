package org.linlinjava.announce.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.CrawledListingMapper;
import org.linlinjava.announce.db.dao.reactive.ListingCategoryMapMapper;
import org.linlinjava.announce.db.dao.reactive.ListingImportRunMapper;
import org.linlinjava.announce.db.domain.CrawledListing;
import org.linlinjava.announce.db.domain.CrawledListingExample;
import org.linlinjava.announce.db.domain.ListingCategoryMap;
import org.linlinjava.announce.db.domain.ListingCategoryMapExample;
import org.linlinjava.announce.db.domain.ListingCategoryMapKey;
import org.linlinjava.announce.db.domain.ListingImportRun;
import org.linlinjava.announce.db.domain.ListingImportRunExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crawled-listing read model: {@code crawled_listing} (JSON column {@code attributes} carried as a String, ids
 * chosen by the importer) with its hand-written importer statements, and the {@code listing_category_map} lookup.
 */
@SpringBootTest
class CrawledListingMapperTest {

    private static final String PREFIX = "it-listing-";
    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired private CrawledListingMapper listings;
    @Autowired private ListingCategoryMapMapper categoryMap;
    @Autowired private ListingImportRunMapper importRuns;

    @AfterEach
    void cleanUp() {
        CrawledListingExample l = new CrawledListingExample();
        l.or().andIdLike(PREFIX + "%");
        listings.deleteByExample(l).block();
        ListingCategoryMapExample m = new ListingCategoryMapExample();
        m.or().andSourceCategoryLike(PREFIX + "%");
        categoryMap.deleteByExample(m).block();
        ListingImportRunExample r = new ListingImportRunExample();
        r.or().andFileLike(PREFIX + "%");
        importRuns.deleteByExample(r).block();
    }

    private static CrawledListing listing(String id, String hash) {
        CrawledListing c = new CrawledListing();
        c.setId(id);
        c.setSource("DBA");
        c.setExternalId(id.substring(PREFIX.length()));
        c.setUrl("https://www.dba.dk/x/id-" + c.getExternalId() + "/");
        c.setContentHash(hash);
        c.setKind("GOODS");
        c.setTitle("Toyota Aygo");
        c.setBrief("Toyota Aygo, 1,0, Benzin, 2007");
        c.setImageUrl("https://billeder.dba.dk/x.jpeg");
        c.setPrice(new BigDecimal("22900.00"));
        c.setCurrency("DKK");
        c.setSaleType("FIXED_PRICE");
        c.setSourceCategory("Cars/Private Car");
        c.setAttributes("{\"km\":\"273000\",\"model_year\":\"2007\"}");
        c.setAddressRaw("6580 Vamdrup");
        c.setCity("Vamdrup");
        c.setPostalCode("6580");
        c.setPublishedAt(NOW.minusDays(1));
        c.setFirstSeenAt(NOW);
        c.setLastSeenAt(NOW);
        c.setStatus("ACTIVE");
        c.setClickCount(0);
        c.setQualityGrade("GOOD");
        c.setQualityIssues("[]");
        c.setAddTime(NOW);
        c.setUpdateTime(NOW);
        return c;
    }

    @Test
    void upsertInsertsThenOverwritesContentButKeepsFirstSeenAndClicks() {
        String id = PREFIX + System.nanoTime();
        CrawledListing first = listing(id, "a".repeat(64));
        StepVerifier.create(listings.upsert(first)).expectNext(1).verifyComplete();
        StepVerifier.create(listings.incrementClickCount(id, NOW)).expectNext(1).verifyComplete();

        CrawledListing second = listing(id, "b".repeat(64));
        second.setPrice(new BigDecimal("21900.00"));
        second.setFirstSeenAt(NOW.plusDays(2));
        second.setLastSeenAt(NOW.plusDays(2));
        second.setClickCount(99);
        // MySQL answers 2 for an updated row of an insert ... on duplicate key update
        StepVerifier.create(listings.upsert(second)).expectNext(2).verifyComplete();

        StepVerifier.create(listings.selectByPrimaryKey(id))
                .assertNext(found -> {
                    assertEquals("b".repeat(64), found.getContentHash());
                    assertEquals(0, new BigDecimal("21900.00").compareTo(found.getPrice()));
                    assertEquals(NOW, found.getFirstSeenAt(), "first_seen_at is never overwritten");
                    assertEquals(NOW.plusDays(2), found.getLastSeenAt());
                    assertEquals(1, found.getClickCount(), "click_count is never overwritten");
                    assertEquals("{\"km\": \"273000\", \"model_year\": \"2007\"}", found.getAttributes());
                })
                .verifyComplete();
    }

    @Test
    void livenessAndQualityColumns() {
        String id = PREFIX + "live-" + System.nanoTime();
        StepVerifier.create(listings.upsert(listing(id, "e".repeat(64)))).expectNext(1).verifyComplete();

        // never checked -> selected first
        StepVerifier.create(listings.selectForLivenessCheck(1000).map(CrawledListing::getId).filter(x -> x.equals(id)))
                .expectNext(id).verifyComplete();

        StepVerifier.create(listings.updateLiveness(id, NOW, true, false)).expectNext(1).verifyComplete();
        StepVerifier.create(listings.updateQuality(id, "WEAK", "[\"image_dead\"]", NOW)).expectNext(1).verifyComplete();
        StepVerifier.create(listings.selectByPrimaryKey(id))
                .assertNext(found -> {
                    assertEquals(NOW, found.getUrlCheckedAt());
                    assertEquals(Boolean.TRUE, found.getUrlOk());
                    assertEquals(Boolean.FALSE, found.getImageOk());
                    assertEquals("WEAK", found.getQualityGrade());
                    assertEquals("[\"image_dead\"]", found.getQualityIssues());
                })
                .verifyComplete();

        // a re-import with the same image keeps the verdict, a new image url resets it; the grade is overwritten
        StepVerifier.create(listings.upsert(listing(id, "f".repeat(64)))).expectNext(2).verifyComplete();
        StepVerifier.create(listings.selectByPrimaryKey(id))
                .assertNext(found -> {
                    assertEquals(Boolean.FALSE, found.getImageOk(), "same image url: verdict kept");
                    assertEquals("GOOD", found.getQualityGrade());
                    assertEquals(NOW, found.getUrlCheckedAt(), "liveness columns are not part of the upsert");
                })
                .verifyComplete();
        CrawledListing newImage = listing(id, "g".repeat(64));
        newImage.setImageUrl("https://billeder.dba.dk/y.jpeg");
        StepVerifier.create(listings.upsert(newImage)).expectNext(2).verifyComplete();
        StepVerifier.create(listings.selectByPrimaryKey(id).map(f -> f.getImageOk() == null))
                .expectNext(true).verifyComplete();

        List<Map<String, Object>> counts = listings.selectInventoryCounts().collectList().block();
        Map<String, Object> row = counts.stream()
                .filter(c -> "DBA".equals(c.get("source")) && "ACTIVE".equals(c.get("status")) && "GOOD".equals(c.get("quality_grade")))
                .findFirst().orElseThrow();
        assertTrue(((Number) row.get("total")).longValue() >= 1);
        assertTrue(((Number) row.get("checked")).longValue() >= 1);
    }

    @Test
    void importRunRows() {
        ListingImportRun run = new ListingImportRun();
        run.setSource("TRADERA");
        run.setFile(PREFIX + "tradera_202610172200.jsonl");
        run.setStartedAt(NOW.minusMinutes(1));
        run.setFinishedAt(NOW);
        run.setSeen(5); run.setInserted(3); run.setUpdated(1); run.setUnchanged(1); run.setRejected(0);
        run.setGood(3); run.setWeak(1); run.setReject(0);
        StepVerifier.create(importRuns.insert(run)).expectNext(1).verifyComplete();
        assertNull(run.getId(), "no generated key is read back; the inventory selects the latest row per source");

        ListingImportRunExample latest = new ListingImportRunExample();
        latest.or().andSourceEqualTo("TRADERA").andFileLike(PREFIX + "%");
        latest.setOrderByClause("finished_at desc, id desc");
        StepVerifier.create(importRuns.selectPageByExample(latest, 0, 1))
                .assertNext(found -> {
                    assertEquals(NOW, found.getFinishedAt());
                    assertEquals(3, found.getGood());
                })
                .verifyComplete();
    }

    @Test
    void touchSeenStatusAndStaleSelection() {
        String stale = PREFIX + "stale-" + System.nanoTime();
        String fresh = PREFIX + "fresh-" + System.nanoTime();
        StepVerifier.create(listings.upsert(listing(stale, "c".repeat(64)))).expectNext(1).verifyComplete();
        StepVerifier.create(listings.upsert(listing(fresh, "d".repeat(64)))).expectNext(1).verifyComplete();
        StepVerifier.create(listings.touchSeen(stale, NOW.minusDays(10))).expectNext(1).verifyComplete();

        StepVerifier.create(listings.selectStaleIds(NOW.minusDays(7), 100).filter(id -> id.startsWith(PREFIX)))
                .expectNext(stale).verifyComplete();

        StepVerifier.create(listings.updateStatus(stale, "GONE", NOW)).expectNext(1).verifyComplete();
        StepVerifier.create(listings.selectStaleIds(NOW.minusDays(7), 100).filter(id -> id.startsWith(PREFIX)))
                .verifyComplete();
        StepVerifier.create(listings.selectByPrimaryKey(stale).map(CrawledListing::getStatus)).expectNext("GONE").verifyComplete();
    }

    @Test
    void categoryMapIsKeyedBySourceAndSourceCategory() {
        ListingCategoryMap row = new ListingCategoryMap();
        row.setSource("DBA");
        row.setSourceCategory(PREFIX + "Cars/Private Car");
        row.setCategoryId(1005001);
        StepVerifier.create(categoryMap.insert(row)).expectNext(1).verifyComplete();

        ListingCategoryMapKey key = new ListingCategoryMapKey();
        key.setSource("DBA");
        key.setSourceCategory(PREFIX + "Cars/Private Car");
        StepVerifier.create(categoryMap.selectByPrimaryKey(key).map(ListingCategoryMap::getCategoryId)).expectNext(1005001).verifyComplete();

        key.setSource("TRADERA");
        StepVerifier.create(categoryMap.selectByPrimaryKey(key)).verifyComplete();
    }
}
