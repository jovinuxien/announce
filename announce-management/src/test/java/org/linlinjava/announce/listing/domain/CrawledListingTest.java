package org.linlinjava.announce.listing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawledListingTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 12, 0);

    static CrawledItem item(String price, String brief) {
        return new CrawledItem(ListingSource.DBA, "1117479380", "https://www.dba.dk/toyota-aygo/id-1117479380/", ListingKind.GOODS,
                "Toyota Aygo", brief, "https://billeder.dba.dk/x.jpeg", price == null ? null : new BigDecimal(price), "DKK",
                ListingSaleType.FIXED_PRICE, null, "Cars/Private Car", Map.of("model_year", "2007", "km", "273000"),
                "6580 Vamdrup", NOW.minusDays(1));
    }

    @Test
    void idIsSourceDashExternalIdAndBriefIsTruncatedTo255() {
        CrawledItem i = item("22900", "x".repeat(300));
        assertThat(i.id()).isEqualTo("dba-1117479380");
        assertThat(i.brief()).hasSize(CrawledItem.BRIEF_LENGTH);
        assertThat(i.attributes().keySet()).containsExactly("km", "model_year");
        assertThatThrownBy(() -> new CrawledItem(ListingSource.DBA, " ", "u", null, "t", null, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("externalId");
    }

    @Test
    void contentHashCoversTheVisibleFieldsOnly() {
        CrawledItem base = item("22900", "brief");
        assertThat(ContentHash.of(base)).hasSize(64).isEqualTo(ContentHash.of(item("22900", "brief")));
        assertThat(ContentHash.of(item("21900", "brief"))).isNotEqualTo(ContentHash.of(base));
        assertThat(ContentHash.of(item("22900", "other"))).isNotEqualTo(ContentHash.of(base));
        CrawledItem laterPublished = new CrawledItem(base.source(), base.externalId(), base.url(), base.kind(), base.title(), base.brief(),
                base.imageUrl(), base.price(), base.currency(), base.saleType(), base.endsAt(), base.sourceCategory(), base.attributes(),
                base.addressRaw(), NOW);
        assertThat(ContentHash.of(laterPublished)).as("published date is not content").isEqualTo(ContentHash.of(base));
    }

    @Test
    void lifecycleFirstSeenSeenAgainStaleGone() {
        CrawledListing first = CrawledListing.firstSeen(item("22900", "brief"), null, 1005007, NOW);
        assertThat(first.status()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(first.address()).isEqualTo(new Address("6580 Vamdrup", "6580", "Vamdrup"));
        assertThat(first.categoryId()).isEqualTo(1005007);
        assertThat(first.contentChanged(item("22900", "brief"))).isFalse();
        assertThat(first.contentChanged(item("21900", "brief"))).isTrue();

        Duration grace = Duration.ofDays(7);
        assertThat(first.isStale(NOW.plusDays(7), grace)).isFalse();
        assertThat(first.isStale(NOW.plusDays(7).plusSeconds(1), grace)).isTrue();

        CrawledListing gone = first.gone();
        assertThat(gone.isActive()).isFalse();
        CrawledListing back = gone.seenAgain(item("21900", "brief"), 7, null, NOW.plusDays(9));
        assertThat(back.status()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(back.firstSeenAt()).isEqualTo(NOW);
        assertThat(back.lastSeenAt()).isEqualTo(NOW.plusDays(9));
        assertThat(back.price()).isEqualByComparingTo("21900");
        assertThat(back.regionId()).isEqualTo(7);
        assertThat(back.clickCount()).isZero();
    }
}
