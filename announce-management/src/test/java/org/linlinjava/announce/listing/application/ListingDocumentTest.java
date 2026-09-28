package org.linlinjava.announce.listing.application;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.listing.domain.CrawledItem;
import org.linlinjava.announce.listing.domain.CrawledListing;
import org.linlinjava.announce.listing.domain.ListingKind;
import org.linlinjava.announce.listing.domain.ListingSaleType;
import org.linlinjava.announce.listing.domain.ListingSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListingDocumentTest {

    @Test
    void usesTheAnnounceFieldNamesPlusSourceAndExternalUrl() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 26, 12, 0);
        LocalDateTime ends = LocalDateTime.of(2026, 10, 1, 18, 30);
        CrawledItem item = new CrawledItem(ListingSource.TRADERA, "640012345", "https://www.tradera.com/item/640012345", ListingKind.GOODS,
                "Volvo V70", "Volvo V70 2.4 D5 2008", null, new BigDecimal("15000"), "SEK", ListingSaleType.AUCTION, ends, "Cars",
                Map.of("model_year", "2008"), "113 45 Stockholm", now.minusDays(2));
        CrawledListing listing = CrawledListing.firstSeen(item, 3, 1005001, now);
        Map<String, Object> d = ListingDocument.of(listing).data();
        assertThat(ListingDocument.of(listing).id()).isEqualTo("tradera-640012345");
        assertThat(d).containsEntry("announce_id", "tradera-640012345")
                .containsEntry("source", "tradera")
                .containsEntry("external_url", "https://www.tradera.com/item/640012345")
                .containsEntry("kind", "GOODS")
                .containsEntry("title", "Volvo V70")
                .containsEntry("description", "Volvo V70 2.4 D5 2008")
                .containsEntry("status", "PUBLISHED")
                .containsEntry("sale_type", "AUCTION")
                .containsEntry("currency", "SEK")
                .containsEntry("category_ids", "1005001")
                .containsEntry("region_id", "3")
                .containsEntry("source_category", "Cars")
                .containsEntry("ends_epoch", ends.toEpochSecond(ZoneOffset.UTC))
                .containsEntry("created_epoch", now.minusDays(2).toEpochSecond(ZoneOffset.UTC))
                .containsEntry("model_year", "2008")
                .doesNotContainKey("image_url");
        assertThat((BigDecimal) d.get("price")).isEqualByComparingTo("15000");
    }
}
