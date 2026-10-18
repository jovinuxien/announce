package org.linlinjava.announce.listing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The grading rules: hard issues reject, soft issues weaken, a clean item is good; the liveness verdict on the image counts. */
class ListingQualityTest {

    static CrawledItem item(String url, String image, BigDecimal price, ListingSaleType sale, LocalDateTime published,
                            String brief, Map<String, String> attributes) {
        return new CrawledItem(ListingSource.DBA, "25925994", url, ListingKind.GOODS, "Peugeot 308", brief, image, price, "DKK",
                sale, null, "Cars/Private Car", attributes, "8520 Lystrup", published);
    }

    static final LocalDateTime PUBLISHED = LocalDateTime.of(2026, 10, 17, 18, 0);
    static final String URL = "https://www.dba.dk/mobility/item/25925994";
    static final String IMAGE = "https://images.dbastatic.dk/dynamic/default/item/25925994/a8d2394c";

    @Test
    void cleanItemIsGood() {
        ListingQuality q = ListingQuality.of(item(URL, IMAGE, new BigDecimal("46000"), ListingSaleType.FIXED_PRICE, PUBLISHED,
                "BlueHDi 120", Map.of("km", "223000")));
        assertThat(q.grade()).isEqualTo(ListingGrade.GOOD);
        assertThat(q.issues()).isEmpty();
    }

    @Test
    void legacyUrlsAndHostsStillPass() {
        ListingQuality q = ListingQuality.of(new CrawledItem(ListingSource.DBA, "1117479380", "https://www.dba.dk/toyota-aygo/id-1117479380/",
                null, "Toyota Aygo", "brief", "https://billeder.dba.dk/dba/9603a3b8.jpeg?class=S300X300", new BigDecimal("22900"), "DKK",
                null, null, null, Map.of("km", "273000"), null, PUBLISHED));
        assertThat(q.grade()).isEqualTo(ListingGrade.GOOD);
        ListingQuality tradera = ListingQuality.of(new CrawledItem(ListingSource.TRADERA, "640012345", "https://www.tradera.com/item/640012345",
                null, "Volvo", "brief", "https://img.tradera.net/small-fit/1/a.jpg", null, "SEK", ListingSaleType.AUCTION, null, null,
                Map.of("year", "2008"), null, PUBLISHED));
        assertThat(tradera.grade()).as("an auction needs no price").isEqualTo(ListingGrade.GOOD);
    }

    @Test
    void missingImageRejectsAndKeepsTheOtherIssues() {
        ListingQuality q = ListingQuality.of(item(URL, null, null, ListingSaleType.FIXED_PRICE, null, "", Map.of()));
        assertThat(q.grade()).isEqualTo(ListingGrade.REJECT);
        assertThat(q.issues()).containsExactly(ListingQuality.NO_IMAGE, ListingQuality.NO_PRICE, ListingQuality.NO_PUBLISHED_AT,
                ListingQuality.NO_BRIEF, ListingQuality.NO_ATTRIBUTES);
    }

    @Test
    void softIssuesWeaken() {
        assertThat(ListingQuality.of(item(URL, IMAGE, new BigDecimal("46000"), ListingSaleType.FIXED_PRICE, null, "b", Map.of("k", "v"))).issues())
                .containsExactly(ListingQuality.NO_PUBLISHED_AT);
        assertThat(ListingQuality.of(item(URL, IMAGE, BigDecimal.ZERO, ListingSaleType.FIXED_PRICE, PUBLISHED, "b", Map.of("k", "v"))).issues())
                .containsExactly(ListingQuality.NO_PRICE);
        assertThat(ListingQuality.of(item("https://www.dba.dk/search?x=1", IMAGE, new BigDecimal("1"), ListingSaleType.FIXED_PRICE, PUBLISHED, "b", Map.of("k", "v"))).issues())
                .containsExactly(ListingQuality.URL_PATTERN);
        assertThat(ListingQuality.of(item(URL, "http://images.dbastatic.dk/x", new BigDecimal("1"), ListingSaleType.FIXED_PRICE, PUBLISHED, "b", Map.of("k", "v"))).issues())
                .containsExactly(ListingQuality.IMAGE_NOT_HTTPS);
        ListingQuality host = ListingQuality.of(item(URL, "https://cdn.example.com/x.jpg", new BigDecimal("1"), ListingSaleType.FIXED_PRICE, PUBLISHED, "b", Map.of("k", "v")));
        assertThat(host.grade()).isEqualTo(ListingGrade.WEAK);
        assertThat(host.issues()).containsExactly(ListingQuality.IMAGE_HOST_UNKNOWN);
    }

    @Test
    void aDeadImageDowngradesAStoredListingAndALiveOneRestoresIt() {
        CrawledListing good = CrawledListing.firstSeen(item(URL, IMAGE, new BigDecimal("46000"), ListingSaleType.FIXED_PRICE, PUBLISHED,
                "b", Map.of("km", "1")), null, null, PUBLISHED);
        assertThat(good.quality().grade()).isEqualTo(ListingGrade.GOOD);
        CrawledListing dead = good.checked(PUBLISHED.plusHours(1), true, false);
        assertThat(dead.quality().grade()).isEqualTo(ListingGrade.WEAK);
        assertThat(dead.quality().issues()).containsExactly(ListingQuality.IMAGE_DEAD);
        assertThat(dead.urlOk()).isTrue();
        assertThat(dead.checked(PUBLISHED.plusHours(2), true, true).quality().grade()).isEqualTo(ListingGrade.GOOD);
        // seen again with the same image keeps the verdict; a new image forgets it
        CrawledItem same = item(URL, IMAGE, new BigDecimal("45000"), ListingSaleType.FIXED_PRICE, PUBLISHED, "b", Map.of("km", "1"));
        assertThat(dead.seenAgain(same, null, null, PUBLISHED.plusDays(1)).quality().grade()).isEqualTo(ListingGrade.WEAK);
        CrawledItem newImage = item(URL, IMAGE + "-2", new BigDecimal("45000"), ListingSaleType.FIXED_PRICE, PUBLISHED, "b", Map.of("km", "1"));
        CrawledListing refreshed = dead.seenAgain(newImage, null, null, PUBLISHED.plusDays(1));
        assertThat(refreshed.imageOk()).isNull();
        assertThat(refreshed.quality().grade()).isEqualTo(ListingGrade.GOOD);
    }
}
