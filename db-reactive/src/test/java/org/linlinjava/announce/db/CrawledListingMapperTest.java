package org.linlinjava.announce.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.dao.reactive.CrawledListingMapper;
import org.linlinjava.announce.db.dao.reactive.ListingCategoryMapMapper;
import org.linlinjava.announce.db.domain.CrawledListing;
import org.linlinjava.announce.db.domain.CrawledListingExample;
import org.linlinjava.announce.db.domain.ListingCategoryMap;
import org.linlinjava.announce.db.domain.ListingCategoryMapExample;
import org.linlinjava.announce.db.domain.ListingCategoryMapKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @AfterEach
    void cleanUp() {
        CrawledListingExample l = new CrawledListingExample();
        l.or().andIdLike(PREFIX + "%");
        listings.deleteByExample(l).block();
        ListingCategoryMapExample m = new ListingCategoryMapExample();
        m.or().andSourceCategoryLike(PREFIX + "%");
        categoryMap.deleteByExample(m).block();
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
